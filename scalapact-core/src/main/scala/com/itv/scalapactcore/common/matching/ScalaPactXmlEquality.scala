package com.itv.scalapactcore.common.matching

import com.itv.scalapactcore.MatchingRule
import com.itv.scalapactcore.common.matching.BodyMatching.BodyMatchingRules

import scala.language.implicitConversions
import scala.xml.{Elem, Node}
import scalaz.Scalaz._

import com.itv.scalapactcore.common.ColourOuput._

object ScalaPactXmlEquality {

  implicit def toXmlEqualityWrapper(xmlElem: Elem): XmlEqualityWrapper = XmlEqualityWrapper(xmlElem)

  case class XmlEqualityWrapper(xml: Elem) {
    def =~(to: Elem): BodyMatchingRules => Boolean = matchingRules =>
      PermissiveXmlEqualityHelper.areEqual(xmlPathToJsonPath(matchingRules), xml, to, xml.label)
    def =<>=(to: Elem): Boolean => BodyMatchingRules => Boolean = beSelectivelyPermissive => matchingRules =>
      StrictXmlEqualityHelper.areEqual(beSelectivelyPermissive, xmlPathToJsonPath(matchingRules), xml, to, xml.label)
  }

  private val xmlPathToJsonPath: BodyMatchingRules => BodyMatchingRules = matchingRules => {

    println("Before: " + matchingRules.map(_.map(_.toString + "\n")).getOrElse("<none>"))

    val res = matchingRules.map { mrs =>
      mrs.map { mr =>
        (mr._1.replaceAll("""\[\'""", ".").replaceAll("""\'\]""", ""), mr._2)
      }
    }

    println("After: " + res.map(_.map(_.toString + "\n")).getOrElse("<none>"))

    res
  }
}

object StrictXmlEqualityHelper {

  def areEqual(beSelectivelyPermissive: Boolean, matchingRules: BodyMatchingRules, expected: Elem, received: Elem, accumulatedXmlPath: String): Boolean = {
    println(">>> Matching Xml (S) >>>")
    (expected.headOption |@| received.headOption) { (e, r) => compareNodes(beSelectivelyPermissive)(matchingRules)(e)(r)(accumulatedXmlPath) } match {
      case Some(bool) => bool
      case None => false
    }
  }

  lazy val compareNodes: Boolean => BodyMatchingRules => Node => Node => String => Boolean = beSelectivelyPermissive => matchingRules => expected => received => accumulatedXmlPath => {

    println(accumulatedXmlPath)

    lazy val prefixEqual = expected.prefix == received.prefix
    lazy val labelEqual = expected.label == received.label
    lazy val attributesLengthOk = expected.attributes.length == received.attributes.length
    lazy val attributesEqual = SharedXmlEqualityHelpers.checkAttributeEquality(matchingRules)(accumulatedXmlPath)(expected.attributes.asAttrMap)(received.attributes.asAttrMap)
    lazy val childLengthOk = expected.child.length == received.child.length

    lazy val childrenEqual =
      if(expected.child.isEmpty) expected.text == received.text
      else {
        expected.child.zip(received.child).forall(p => compareNodes(beSelectivelyPermissive)(matchingRules)(p._1)(p._2)(accumulatedXmlPath + "." + expected.label))
      }

    prefixEqual && labelEqual && attributesLengthOk && attributesEqual && childLengthOk && childrenEqual
  }

}

object PermissiveXmlEqualityHelper {

  /***
    * Permissive equality means that the elements and fields defined in the 'expected'
    * are required to be present in the 'received', however, extra elements on the right
    * are allowed and ignored. Additionally elements are still considered equal if their
    * fields or array elements are out of order, as long as they are present since json
    * doesn't not guarantee element order.
    */
  def areEqual(matchingRules: BodyMatchingRules, expected: Elem, received: Elem, accumulatedXmlPath: String): Boolean = {
    println(">>> Matching Xml (P) >>>")
    (expected.headOption |@| received.headOption) { (e, r) => compareNodes(matchingRules)(e)(r)(accumulatedXmlPath) } match {
      case Some(bool) => bool
      case None => false
    }
  }

  lazy val compareNodes: BodyMatchingRules => Node => Node => String => Boolean = matchingRules => expected => received => accumulatedXmlPath => {

    // val rulesMap = e.flatMap { ex =>
    //   Map(ex._1 -> findMatchingRules(accumulatedXmlPath + ".@" + ex._1)(matchingRules).getOrElse(Nil))
    // }.filter(_._2.nonEmpty)

    // println(accumulatedXmlPath)

    SharedXmlEqualityHelpers.matchNodeWithRules(matchingRules)(accumulatedXmlPath)(expected)(received) match {
      case RuleMatchSuccess => true
      case RuleMatchFailure => false
      case NoRuleMatchRequired =>
        lazy val prefixEqual = expected.prefix == received.prefix
        lazy val labelEqual = expected.label == received.label
        lazy val attributesEqual = SharedXmlEqualityHelpers.checkAttributeEquality(matchingRules)(accumulatedXmlPath)(expected.attributes.asAttrMap)(received.attributes.asAttrMap)
        lazy val childLengthOk = expected.child.length <= received.child.length

        // println("prefixEqual: " + prefixEqual)
        // println("labelEqual: " + labelEqual)
        // println("attributesEqual: " + attributesEqual)
        // println("childLengthOk: " + childLengthOk)

        lazy val childrenEqual =
          if(expected.child.isEmpty) expected.text == received.text
          else expected.child.forall { eN => received.child.exists(rN => compareNodes(matchingRules)(eN)(rN)(accumulatedXmlPath + "." + eN.label)) }

        prefixEqual && labelEqual && attributesEqual && childLengthOk && childrenEqual
    }
  }

}

object SharedXmlEqualityHelpers {

  lazy val checkAttributeEquality: BodyMatchingRules => String => Map[String, String] => Map[String, String] => Boolean = matchingRules => accumulatedXmlPath => e => r => {

    val rulesMap = e.flatMap { ex =>
      Map(ex._1 -> findMatchingRules(accumulatedXmlPath + ".@" + ex._1)(matchingRules).getOrElse(Nil))
    }.filter(_._2.nonEmpty)

    val (attributesWithRules, attributesWithoutRules) = e.partition(p => rulesMap.contains(p._1))

    if(attributesWithRules.isEmpty){
      SharedXmlEqualityHelpers.mapContainsMap(e)(r)
    } else {
      attributesWithRules.forall(attributeRulesCheck(rulesMap)(r)) && mapContainsMap(attributesWithoutRules)(r)
    }
  }

  lazy val mapContainsMap: Map[String, String] => Map[String, String] => Boolean = e => r =>
    e.forall { ee =>
      r.exists(rr => rr._1 == ee._1 && rr._2 == ee._2)
    }

  private lazy val attributeRulesCheck: Map[String, List[MatchingRuleContext]] => Map[String, String] => ((String, String)) => Boolean = rulesMap => receivedAttributes => attribute =>
    rulesMap(attribute._1).map(_.rule).forall(attributeRuleTest(receivedAttributes)(attribute))

  private lazy val attributeRuleTest: Map[String, String] => ((String, String)) => MatchingRule => Boolean = receivedAttributes => attribute => rule => {
    val a = MatchingRule.unapply(rule)

    MatchingRule.unapply(rule) match {
      case Some((Some(matchType), None, None)) if matchType == "type" =>
        true //Always a string? Hmmmmm... Attributes are strings but they contain other types.

      case Some((Some(matchType), Some(regex), _)) if matchType == "regex" =>
        receivedAttributes
          .get(attribute._1)
          .map(_.matches(regex))
          .getOrElse(false)

      case _ =>
        println("Unexpected match type during attribute matching with rule".red)
        false

    }
  }

  private val findMatchingRules: String => BodyMatchingRules => Option[List[MatchingRuleContext]] = accumulatedJsonPath => m =>
    if (accumulatedJsonPath.length > 0) {
      m.flatMap(
        _.map(r => (r._1.replace("['", ".").replace("']", ""), r._2)).filter { r =>
          r._1.endsWith(accumulatedJsonPath) || WildCardRuleMatching.findMatchingRuleWithWildCards(accumulatedJsonPath)(r._1)
        }.map(kvp => MatchingRuleContext(kvp._1.replace("$.body", ""), kvp._2)).toList.some
      )
    } else None

  val matchNodeWithRules: BodyMatchingRules => String => Node => Node => ArrayMatchingStatus = matchingRules => accumulatedXmlPath => ex => re => {

    println("****** matchNodeWithRules ******")

    println(accumulatedXmlPath + " : " + ex)

    val rules = matchingRules.map { mrs =>
      mrs.filter(mr => mr._1.replace("$.body.", "").startsWith(accumulatedXmlPath))
    }.getOrElse(Map.empty[String, MatchingRule])

    val results = rules.map { rule =>
      rule._1.replace("$.body.", "").replace(accumulatedXmlPath, "") match {
        case r: String if r.startsWith(".@") =>
          val attribute = (r.replace(".@", ""), "")
          if(attributeRuleTest(re.attributes.asAttrMap)(attribute)(rule._2)) RuleMatchSuccess
          else RuleMatchFailure

        case r: String =>
          println(s"Unexpected rule: $r".yellow)
          RuleMatchFailure
      }
    }

    println(rules)
    println(results)

    println()

    ArrayMatchingStatus.listArrayMatchStatusToSingle(RuleMatchFailure :: Nil)
  }

//  def compareValues(matchingRules: BodyMatchingRules, expected: Json, received: Json, accumulatedJsonPath: String): Boolean =
//    (matchingRules >>= findMatchingRules(accumulatedJsonPath)).map(_.map(_.rule)) match {
//      case Some(rules) if rules.nonEmpty =>
//        rules.forall {
//          case rule if rule.`match`.exists(_ == "type") => //Use exists for 2.10 compat
//            expected.name == received.name
//
//          case rule if received.isString && rule.`match`.exists(_ == "regex") && rule.regex.isDefined => //Use exists for 2.10 compat
//            rule.regex.exists { regexRule =>
//              received.string.exists(_.matches(regexRule))
//            }
//
//          case rule =>
//            println(("Found unknown rule '" + rule + "' for path '" + accumulatedJsonPath + "' while matching " + expected.toString + " with " + received.toString()).yellow)
//            false
//        }
//
//      case Some(rules) if rules.isEmpty =>
//        expected == received
//
//      case None =>
//        expected == received
//    }
//
//  def matchArrayWithRules(matchingRules: BodyMatchingRules, expectedArray: Option[Json.JsonArray], receivedArray: Option[Json.JsonArray], accumulatedJsonPath: String): ArrayMatchingStatus = {
//
//    def checkRule(currentPath: String, ruleAndContext: MatchingRuleContext, ea: Json.JsonArray, ra: Json.JsonArray): ArrayMatchingStatus = {
//
//      //TODO: Missing regex...?
//      if(currentPath == ruleAndContext.path) {
//        MatchingRule.unapply(ruleAndContext.rule).map {
//          case (None, None, Some(arrayMin)) =>
//            if (ra.length >= arrayMin) RuleMatchSuccess
//            else RuleMatchFailure
//
//          case (Some(matchType), None, Some(arrayMin)) if matchType == "type" =>
//            // Yay typed languages! We know the types are equal, they're both arrays!
//            if (ra.length >= arrayMin) RuleMatchSuccess
//            else RuleMatchFailure
//
//          case (Some(matchType), None, None) if matchType == "type" =>
//            // Yay typed languages! We know the types are equal, they're both arrays!
//            RuleMatchSuccess
//
//          case _ =>
//            NoRuleMatchRequired
//        }.getOrElse(NoRuleMatchRequired)
//      } else if(ruleAndContext.path.contains("*")) {
//        // We have a rule that isn't a simple match on the path and includes a wildcard.
//        WildCardRuleMatching.arrayRuleMatchWithWildcards(currentPath)(ruleAndContext)(ea)(ra)
//      } else if(ruleAndContext.path.startsWith(currentPath + "[")) {
//        // We have a rule that isn't a simple match on the path and may be an array positional match like fish[2]
//        WildCardRuleMatching.arrayRuleMatchWithWildcards(currentPath)(ruleAndContext)(ea)(ra)
//      } else {
//        println(("Unknown rule type: '" + ruleAndContext.rule + "' for path '" + ruleAndContext.path).yellow)
//        RuleMatchFailure
//      }
//    }
//
//    (expectedArray |@| receivedArray) { (ja, ra) =>
//      matchingRules >>= findMatchingRules(accumulatedJsonPath) match {
//
//        case Some(rules) =>
//          ArrayMatchingStatus.listArrayMatchStatusToSingle(rules.map(r => checkRule(accumulatedJsonPath, r, ja, ra)))
//
//        case None =>
//          NoRuleMatchRequired
//
//      }
//    }.getOrElse(NoRuleMatchRequired)
//  }

}
