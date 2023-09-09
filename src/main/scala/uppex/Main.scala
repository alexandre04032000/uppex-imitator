package uppex

import org.apache.commons.math3.stat.StatUtils.mode
import org.apache.poi.ss.usermodel.WorkbookFactory
import uppex.backend.{RunUppaal, RunImitator_PProd}
import uppex.semantics.GenModel
//import uppex.semantics.Imitator.{AnnotationBl, Model =>IModel, getDiff, getPrettyDiff}
//import uppex.semantics.Uppaal.{AnnotationBl, Model => UModel, XmlElm, getDiff, getPrettyDiff}
import uppex.syntax.{ExcelParser, ImitatorParser, Report, UppaalParser}
import uppex.semantics.{Annotations, Configurations, Imitator, Uppaal, GenModel}
import uppex.semantics.GenModel.{AnnotationBl, Block, Content, XmlElm,getDiff, getPrettyDiff}

import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat
import java.util.Calendar
import scala.language.postfixOps
import sys.process.*


object Main:
  // when extending App, `args` is alyways null
  def main(args: Array[String]): Unit = try
    println("Starting Running Uppex")
    //val Flag = getFileNames(args.toList(1))._2.endsWith(".imi")
    def help: Unit =  println("Usage: uppex.jar [--run | --runAll] [-t <timout>] [--info] [-p productName] [--validate] <inputFile.xlsx>")
    if args == null then help
    else args.toList match
      case "--help"::_ | "-h"::_ => help
      case "--functions"::_ | "-f"::Nil =>
        println(org.apache.poi.ss.formula.eval.FunctionEval.getSupportedFunctionNames.toArray.mkString("\n"))
      case "--info"::"-p"::prod::baseName::Nil =>
         applyProperties(baseName,prod)
      case "--runAll"::baseName::Nil =>
         runAllChecks(baseName)
      case "--runAll"::"-t"::n::baseName::Nil =>
         runAllChecks(baseName,n.toInt)
      case "--run"::"-p"::prod::baseName::Nil =>
         runChecks(baseName,prod)
      case "--run"::"-t"::n::"-p"::prod::baseName::Nil =>
         runChecks(baseName,prod,n.toInt)
      case "--run"::baseName::Nil =>
         runChecks(baseName,"Main")
      case "--validate" :: baseName :: _ =>
         validate(baseName)

      case baseName::Nil if !baseName.startsWith("-") =>
         applyAndUpdateModel(baseName)
      case "-p"::prod::baseName::Nil =>
         applyAndUpdateModel(baseName)
      case x => println(s"Unknown options: ${x.mkString(" ")}"); help
  catch
    case e:Throwable => System.err.println(s"Error: ${e.getMessage}")



//  @main
  def applyAndUpdateModel(baseName:String, product:String="Main"): Unit =
    applyProperties(baseName,product) match
      case (model,original,_,uppFileName,true) => updateModel(model,original,uppFileName)
      case _ =>


  def getFileNames(basename:String): (String,String) =
    val res =
      if basename.toLowerCase.endsWith(".xlsx") then
        //(basename,basename.dropRight(4)+"xml")
        throw new RuntimeException(s"Format Not Supported.")
      else if basename.toLowerCase.endsWith(".imi") then
        (basename.dropRight(3)+"xlsx",basename)
      else if basename.toLowerCase.endsWith(".xml") then
        (basename.dropRight(3)+"xlsx",basename)
      else (basename+".xlsx",basename+".imi")

    if !(new File(res._1).exists) then
      throw new RuntimeException(s"File ${res._1} not found.")
    if !(new File(res._2).exists) then
      throw new RuntimeException(s"File ${res._2} not found.")
    res


  /**
   * Reads the excel and imitator files, and inspects the configurations and the feature model.
   * @param baseName
   * @param product
   * @return the imitator model, the original uppal model (string), the configurations in the spreadsheets, and the imitator filename.
   */


  def run: Unit = runAllChecks("C:\\Users\\Lenovo\\IdeaProjects\\uppex\\examples\\Teste_novo_excel\\Imitator_Template.imi")
  def run2: Unit = validate("C:\\Users\\Lenovo\\IdeaProjects\\uppex\\examples\\Teste_novo_excel\\Imitator_Template.imi")
  def validate(baseName:String, product:String = ""): (GenModel,String,Configurations,String) =
    val (propFile, uppFile) = getFileNames(baseName)
    //    val propFile = baseName+".xlsx"
    //    val uppFile = baseName+".xml"

    println(s"> Reading properties from '$propFile'")

    val conf = ExcelParser.parse(propFile, product)
    if conf.featModel.isEmpty then println(" - No FM found") else println(s"---\n${conf.featModel.get}\n---")


    //println(s"> Reading Imitator file '$uppFile'")

    if uppFile.toLowerCase.endsWith(".imi") then {
      println(s"> Reading Imitator file '$uppFile'")
      val (model, original) = ImitatorParser.parseFile(uppFile, conf)
      println(s" - Products: ${conf.products.keys.mkString(", ")}")
      println(" - Configured annotations: " + (for AnnotationBl(a, _, _) <- model.blocks yield a).mkString(", "))

      val queries = conf.annotations.get("queries")
      val total = queries.map(_.attrs.size).getOrElse(0)

      (model,original,conf,uppFile)
    } else {
      println(s"> Reading UPPAAL file '$uppFile'")
      val (model, original) = UppaalParser.parseFile(uppFile, conf)
      println(s" - Products: ${conf.products.keys.mkString(", ")}")
      println(" - Configured annotations: " + (for AnnotationBl(a, _, _) <- model.blocks yield a).mkString(", "))
      if uppFile.toLowerCase.endsWith(".xml") then {
        println(" - Configured tags: " + (for XmlElm(a, _, _) <- model.blocks yield a).mkString(", "))
      } else {
        println("File does not have xml tags")
      }
      //println(model)

      (model, original, conf, uppFile)
    }


  def applyProperties(baseName: String, product: String): (GenModel, String, Configurations, String, Boolean) =
    val (model, original, conf, uppFile) = validate(baseName, product)

    if getDiff(model).isEmpty then
      println(s"\n> No differences detected. File '$uppFile' not updated.")
      (model, original, conf, uppFile, false)
    else
      println(getPrettyDiff(model))
      (model, original, conf, uppFile, true)
//      


  private def updateModel(model: GenModel, original: String, uppFile: String): Unit =
    backupOld(model, uppFile, original: String)
    println(s"\n> Updating file '$uppFile'")
    val pw = new PrintWriter(new File(uppFile))
    pw.write(model.buildNew)
    /*
    model match
      case  m:Uppaal.Model => pw.write(Uppaal.buildNew(m))
      case  m:Imitator.Model => pw.write(Imitator.buildNew(m))
      case _ => sys.error(s"Unexpected model ${model.getClass}")
     */
    pw.close()



  
  private def backupOld(model: GenModel, modelFileName: String, original: String): Unit =
    val ext = model match
      case Uppaal.Model(_) => ".xml"
      case Imitator.Model(_) => ".imi"
      case _ => sys.error(s"Unknown Model ${model.getClass}")

    val ext2 = model.extension

    val backupFile = modelFileName.dropRight(4)+"-"+
      (new SimpleDateFormat("yy-MM-dd_HH.mm.ss"))
        .format(Calendar.getInstance.getTime)+
      ext
    println(s"\n> Backing up previous version in 'backups/$backupFile'")

    val file = new File(s"backups/$backupFile")
    require(file.getParentFile.exists() || file.getParentFile.mkdirs(),
      "Backup's directory creation failed")
    val pw = new PrintWriter(file)
    pw.write(original) // Safer with `original`, but would also work: Imitator.buildOld(model)
    pw.close()


  private def runAllChecks(basename:String, timeout:Int = 30): Unit =
    val (excel,filename) = getFileNames(basename)
    //    val excel = basename+".xlsx"
    val conf = ExcelParser.parse(excel,"Main") // need to discover the products first
    if filename.toLowerCase.endsWith(".imi") then
      if conf.featModel.isEmpty then println(" - No FM found") else println(s"---\n${conf.featModel.get}\n---")
      println(s" - Products: ${conf.products.keys.mkString(", ")}")

      println(s"> Reading Imitator file '$filename'")
      val rep = new Report(basename, timeout)
      for prod <- conf.products.keys if prod != "" do
        val confProd = ExcelParser.parse(excel, prod)
        val (uppModel, _) = ImitatorParser.parseFile(filename, confProd)
        //val a = confProd.annotations.get("Limits").get.attrs.values.toList.sortWith((x, y) => x._1 < y._1)
        //confProd.annotations.updateField1IfField0Equals("Teste")
        //println(confProd.annotations.get("Limits"))
        println(confProd.products(prod))
        //println(confProd.annotations.get("Limits"))

        def corrigirCodigo(scalaCode: String): String = {
          val lines = scalaCode.split("\n").toList

          val fixedLines = lines.foldRight(List.empty[String]) { (line, acc) =>
            if (acc.nonEmpty && acc.head.trim == ":parameter ;") then
              acc.head :: line.replace("=", "").trim :: acc.tail
            else {
              line :: acc
            }
          }

          fixedLines.mkString("\n")
        }

        def encontrarLinhasComIgual(scalaCode: String): String = {
          val lines = scalaCode.split("\n").toList
          val regex = ".*[^:]\\s*=$".r
          lines.map { line =>
            if (regex.matches(line.trim)) then line.replace("=", "").trim
            else line
          }.mkString("\n")
        }

        //println(encontrarLinhasComIgual(uppModel.buildNew))
        //println(uppModel.buildOld)
        //println(confProd.products(prod).toList)
        //println("Separation")
        //println(confProd.annotations.get("queries"))
        //println("Separation")
        //println(Imitator.buildOld(uppModel))
        //val limites = confProd.annotations.get("Limits")
        RunImitator_PProd.checkProductImitator(prod, confProd, uppModel, timeout, rep)
        //RunImitator.checkProductImitator(prod, confProd, uppModel, timeout)
      rep.writeFile(s"report_Imitator.html")


    if filename.toLowerCase.endsWith(".xml") then {
      if conf.featModel.isEmpty then println(" - No FM found") else println(s"---\n${conf.featModel.get}\n---")
      println(s" - Products: ${conf.products.keys.mkString(", ")}")

      println(s"> Reading Uppaal file '$filename'")
      val rep = new Report(basename, timeout)
      for prod <- conf.products.keys if prod != "" do
        val confProd = ExcelParser.parse(excel, prod)
        val (uppModel, _) = UppaalParser.parseFile(filename, confProd)
        RunUppaal.checkProduct(prod, confProd, uppModel, timeout, rep)
      rep.writeFile(s"report.html")
    }


  private def runChecks(basename:String,prod:String, timeout:Int = 30): Unit =
    val (excel,upp) = getFileNames(basename)
    val conf = ExcelParser.parse(excel,prod)

    if conf.featModel.isEmpty then println(" - No FM found") else println(s"---\n${conf.featModel.get}\n---")
    println(s" - Products: ${conf.products.keys.mkString(", ")}")

    if upp.toLowerCase.endsWith(".imi") then
      println(s"> Reading Imitator file '$upp'")
      val rep = new Report(basename, timeout)
      val (uppModel, _) = ImitatorParser.parseFile(upp, conf)
      RunImitator_PProd.checkProductImitator(prod, conf, uppModel, timeout, rep)
      rep.writeFile(s"report_Imitator.html")

    if upp.toLowerCase.endsWith(".xml") then {
      println(s"> Reading Uppaal file '$upp'")
      val rep = new Report(basename, timeout)
      val (uppModel, _) = UppaalParser.parseFile(upp, conf)
      RunUppaal.checkProduct(prod, conf, uppModel, timeout, rep)
      rep.writeFile(s"report.html")
    }

    //    val confProd = ExcelParser.parse(excel, prod)
    //    val file = File.createTempFile(upp.dropRight(4), ".xml")
    //    val (model, original) = ImitatorParser.parseFile(upp, confProd)





//  private def runChecks2(basename:String,prod:String) =
//    val (model,content,conf,uppFile,updated) = applyProperties(basename, prod)
//    val file = File.createTempFile(s"$uppFile-",".xml");
//    println(s"\n> running: verifyta ${file.getAbsolutePath}")
//    val pw = new PrintWriter(file)
//    pw.write(Imitator.buildNew(model))
//    pw.close()
//    val reply = s"verifyta ${file.getAbsolutePath}".!!
//    val answ = reply.split("Formula is ").map(!_.startsWith("NOT")).toList.tail
//    val queries = conf.xmlBlocks.get("queries")
//    val comments = for
//      qs <- queries.toList
//      line <- qs.attrs
//      comm <- line._2._2.get(qs.header.indexOf("Comment"))
//    yield
//      comm
//
//    println(comments.zip(answ).map((s,b) => s"[${if b then "OK" else "FAIL"}] $prod: $s").mkString("\n"))
//
////    println(s"reply: $reply")
////    println(s"answer: $answ")
////    println(s"queries: $queries")
////    println(s"x: $comments")
////    println(s"zip: ${comments zip answ}")
//
//



