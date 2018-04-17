#!/bin/bash

source scripts/test-header.sh

echo ""
echo "Checking verifier..."

CORE_VERSION=$(grep "version :=" build.sbt | sed 's/version :=//g' | sed 's/ //g' | sed 's/,//g' | sed 's/\"//g')

remove_plugin_file
remove_config_file

echo "addSbtPlugin(\"com.itv\" % \"sbt-scalapact\" % \"$CORE_VERSION\")" >> $PLUGIN_FILE

cat > $PACT_CONFIG_FILE <<EOL
import com.itv.scalapact.plugin.ScalaPactPlugin._

providerStates := Seq(
("Resource with ID 1234 exists", (_: String) => {
 println("Injecting key 1234 into the database...")
 // Do some work to ensure the system under test is
 // in an appropriate state before verification

 true
})
)

providerStateMatcher := {
case key if key == "Resource with ID 1234 exists" =>
 println("Injecting key 1234 into the database...")
 true
}

pactBrokerAddress := "http://localhost"
providerName := "Their Provider Service"
consumerNames := Seq("My Consumer")
pactContractVersion := "2.0.0"
allowSnapshotPublish := false
EOL

sbt "project testsWithDeps" update
sbt "; project testsWithDeps; pact-stubber --port 1234" &

COUNTDOWN=45

echo "...giving the stubber a $COUNTDOWN second head start to warm up..."
simple_countdown $COUNTDOWN

echo "Verifying..."

sbt "; project testsWithDeps; pact-verify --source target/pacts"

pkill -1 -f sbt-launch.jar

remove_plugin_file
remove_config_file