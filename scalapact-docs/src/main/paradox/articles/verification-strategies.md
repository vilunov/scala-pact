# Verification strategies

**Note:** See the @ref:[example projects](../examples/index.md) for live, minimal versions you can try yourself.

Verification is the process of checking that a Pact contract, generated by a consumer, is being adhered to by the provider service.

There are two approaches to doing this:
1. External: Stubbing and injecting data into a running service
2. Internal: Running contract verification as part of test suite.

They are being referred to here as internal and external simply because one operates from within your code base and the other has no access.

Both are equally valid and both require thought about which strategy will work best in your scenario and effort to setup.

### As a rough guide...
Prefer internal verification where possible. It's a far lighter and faster way to verify your contracts. It runs at the speed of your normal unit tests and requires no orchestration or provider states, just a test suite. The process of setting up your service in such a way that internal verification is possible also encourages strong separations of concerns between routing / marshalling and business logic.

External verification is good for...
- Services that hold no data or have no data store
- Older services that are hard to modify / retro fit

Internal verification is good for...
- Any type of service provided you can organise the project accordingly
- Services that rely on data stores (since they can be mocked)


## External verification

This is blackbox testing. The aim is to run your service in an isolated state, fire requests at it and check the responses.

How do you isolate a service that has databases and upstream services?

Upstream services are the easy bit, they are your providers and so you wrote Pact tests for them didn't you? Therefore all you need to do is run the pact stubber and it will pretend to be your upstream services for you.

Data stores are more challenging. The idea here is to set up a locally running version of the datastore with know preloaded data.

The local datastore could be a true local instance, it would be an in-memory protocol-equivalent store that your service knowns how to run on startup, it could also be a something like a docker image.

Data injection is done using @ref:[provider states](../advanced/provider-states.md). A provider state is simply a hook with a string key that gives you an opportunity to set up the state of the provider before the verification is done. In Scala-Pact, provider states are setup in your SBT files and hence you can use any scala libraries or constructs you like or even break out back to a shell script.

Once your service is isolated, you then run the @ref:[verifier](../basics/sbt-commands.md) against your service.

## Internal verification

Internal verification is done through a test suite. Really all that happens here is that the normal verifier is invoked from inside a test case when you are ready, but this allows some different setup opportunities.

The first thing to note is that you have full access to your codebase so now you can use your fancy functions for doing data base setup for instance.

A better strategy though maybe, in this type of verification, to view this as the inverse of the consumer tests. The consumer tests only test the small piece of code that actually handles connecting to the provider. We can do the same here, isolate the part of the provider responsible for adhereing to the contract i.e. the routing and marshalling code.

Please see the @ref:[example projects](../examples/index.md) for working demonstrations of both approaches.