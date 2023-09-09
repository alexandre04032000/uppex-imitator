package uppex.syntax
import math.Ordered.orderingToOrdered
import uppex.semantics.Configurations.FProd
import uppex.semantics.Imitator.*
import uppex.semantics.{Annotations, Configurations}
import ExcelParser.{Loc, show as showL}
import uppex.syntax.FeatExprParser.FeatVal
import uppex.syntax.FeatExprParser.FeatVal.{Opt, Value}

import scala.io.Source
import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers

object FeatExprParser extends RegexParsers:

//  override val skipWhitespace = false
  override def skipWhitespace = true
  override val whiteSpace: Regex = "( |\t|\n)+".r

  ////////////////////

  enum FeatVal:
    case Feature(id:String)
    case Value(n:Double)
    case Opt(o:String)

  enum FeatExpr:
    case Feature(id:String)
    case And(f1:FeatExpr,f2:FeatExpr)
    case Or(f1:FeatExpr,f2:FeatExpr)
    case Imply(f1:FeatExpr,f2:FeatExpr)
    case Not(f:FeatExpr)
    case Comp(v1:FeatVal,v2:FeatVal,op:String)
    case True

  import FeatExpr._


  def vars(fe:FeatExpr): Set[String] = fe match
    case Feature(id) => Set(id)
    case And(f1, f2) => vars(f1) ++ vars(f2)
    case Or(f1, f2) => vars(f1) ++ vars(f2)
    case Imply(f1,f2) => vars(f1) ++ vars(f2)
    case Comp(n1,n2,_) => vars(n1) ++ vars(n2)
    case Not(f2) => vars(f2)
    case True => Set()

  def vars(fv:FeatVal): Set[String] = fv match
    case FeatVal.Feature(id) => Set(id)
    case FeatVal.Value(_) => Set()
    case FeatVal.Opt(_) => Set()

  def show(fe:FeatExpr): String = fe match
    case Feature(id) => id
    case And(f1, f2) => showP(f1)+" && "+showP(f2)
    case Or(f1, f2) => showP(f1)+" || "+showP(f2)
    case Imply(f1, f2) => showP(f1)+" => "+showP(f2)
    case Not(True) => "false"
    case True => "true"
    case Not(f2) => "!"+showP(f2)
    case Comp(v1,v2,op) => show(v1)+op+show(v2)
  private def showP(fe:FeatExpr): String = fe match
    case _:(And|Or) => s"(${show(fe)})"
    case _ => show(fe)

  private def show(v:FeatVal): String = v match
    case FeatVal.Feature(id) => id
    case FeatVal.Value(n) => n.toString
    case FeatVal.Opt(o) => o


  def eval(fe:FeatExpr)(using prod:FProd): Boolean = fe match
    case Feature(id) => prod contains id
    case And(f1,f2) => eval(f1) && eval(f2)
    case Or(f1,f2) => eval(f1) || eval(f2)
    case Imply(f1, f2) => !eval(f1) || eval(f2)
    case Not(f2) => !eval(f2)
    case Comp(v1,v2,op) => getResult(Comp(v1,v2,op))
    case True => true

  def evalC(v:FeatVal)(using prod:FProd) : Any = v match
    case FeatVal.Feature(id) =>
      prod.get(id) match
        case Some(value: Any) =>
          try {
            value.toString.toDouble
          }
          catch {
            case _: NumberFormatException => value.toString
          }
        case None => throw new IllegalArgumentException(s"Feature $id do not found in ${prod.keySet.mkString(",")}.")
    case FeatVal.Value(n) => n
    case FeatVal.Opt(o) => o
    case _ => throw new IllegalArgumentException("Valor não reconhecido para comparação.")

  def getResult(op: FeatExpr)(using prod:FProd): Boolean = op match
    case Comp(v1, v2, ">") =>
      (evalC(v1), evalC(v2)) match
        case (a: Double, b: Double) => a > b
        case _ => throw new IllegalArgumentException("Operação '>' só é suportada entre números.")
    case Comp(v1, v2, "<") =>
      (evalC(v1), evalC(v2)) match
        case (a: Double, b: Double) => a < b
        case _ => throw new IllegalArgumentException("Operação '<' só é suportada entre números.")
    case Comp(v1,v2,"==") => evalC(v1) == evalC(v2)
    case Comp(v1, v2, ">=") =>
      (evalC(v1), evalC(v2)) match
        case (a: Double, b: Double) => a >= b
        case _ => throw new IllegalArgumentException("Operação '>=' só é suportada entre números.")
    case Comp(v1, v2, "<=") =>
      (evalC(v1), evalC(v2)) match
        case (a: Double, b: Double) => a <= b
        case _ => throw new IllegalArgumentException("Operação '<=' só é suportada entre números.")
    case Comp(v1,v2,"!=") => evalC(v1) != evalC(v2)
    case _    => throw new IllegalArgumentException(s"Operador desconhecido: $op")


  case class ParseException(msg:String) extends RuntimeException(msg)

  def parse(txt: String, loc:Loc)(using sheet:String=""): FeatExpr =
    parseAll(featExpr, txt) match
      case Success(result, _) => result
      case f: NoSuccess => throw
        ParseException(s"Failed to parse feature expression in ${showL(loc)}: '$txt' -- $f")

  ///////////////////

  def featExpr: Parser[FeatExpr] =
    featImpl |
    ("" ^^^ True)

  def featImpl: Parser[FeatExpr] =
    featDisj ~ opt(("->"|"=>"|"<->"|"<=>"|"#")~featImpl) ^^ {
      case f1 ~ Some("->",f2) => Imply(f1, f2)
      case f1 ~ Some("=>",f2) => Imply(f1, f2)
      case f1 ~ Some("<->",f2) => And(Imply(f1, f2),Imply(f1, f2))
      case f1 ~ Some("<=>",f2) => And(Imply(f1, f2),Imply(f1, f2))
      case f1 ~ Some(_,f2) => Or(Not(f1), Not(f2)) // #
      case f1 ~ None => f1
    }
  def featConj: Parser[FeatExpr] =
    literal ~ opt("&&"~>featConj) ^^ {
      case f1 ~ Some(f2) => And(f1, f2)
      case f1 ~ None => f1
    }


  def featDisj: Parser[FeatExpr] =
    featConj ~ opt("||"~>featDisj) ^^ {
      case f1 ~ Some(f2) => Or(f1, f2)
      case f1 ~ None => f1
    }

  def optionalFeature: Parser[FeatExpr] =
    "?" ~> feature ^^ {
      case f: Feature => Or(True, f)
    }

  def featComp: Parser[FeatExpr] =
    featureVal ~ ("<" | ">" | ">=" | "<=" | "==" | "!=") ~ featValOpt ^^ {
      case n1 ~ op ~ n2 => Comp(n1, n2, op)
    }

  def literal: Parser[FeatExpr] =
    "("~>featImpl<~")" |
    "!"~>literal ^^ Not.apply |
    "true" ^^^ True |
    "false" ^^^ Not(True) |
      featComp|
    feature

  def feature: Parser[FeatExpr] =
    """[a-zA-Z0-9_][a-zA-Z\-0-9_]*""".r ^^ Feature.apply

  def featureVal: Parser[FeatVal] =
    """[a-zA-Z_][a-zA-Z\-0-9_]*""".r ^^ FeatVal.Feature.apply
    //"""[0-9]+""".r ^^ (x => FeatVal.Value(x.toDouble)) |  //extend to floats
    //rep1sep("""[a-zA-Z_][a-zA-Z\-0-9_]*""".r, ",") ^^ (list => FeatVal.Opt(list.mkString(",")))

  def featValOpt: Parser[FeatVal] =
    """[0-9]+""".r ^^ (x => FeatVal.Value(x.toDouble)) | //extend to floats
    """\[[a-zA-Z_,\-]+\]""".r ^^ FeatVal.Opt.apply |
    """[\??,a-zA-Z_,\-]+""".r ^^ FeatVal.Opt.apply








