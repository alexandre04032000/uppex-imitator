package uppex.backend

import uppex.semantics.{Annotations, Configurations, Imitator}

import java.io.{File, IOException, PrintWriter}
import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.file.{Files, Paths}
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.io.File
import java.nio.file.{Files, Paths, StandardCopyOption}
import uppex.semantics.{Configurations, Imitator}
import uppex.syntax.FeatExprParser.FeatExpr.Or
import uppex.syntax.Report

import scala.io.Source
import java.io.{File, IOException, PrintWriter}
import java.nio.file.{Files, Paths}
import sys.process.*
import scala.util.Properties
import scala.collection.mutable.ListBuffer
import scala.util.{Try, Success, Failure}

object RunImitator_PProd {
  //Função Principal
  def checkProductImitator(prod: String, confProd: Configurations, model: Imitator.Model, timeout: Int, rep: Report) =
    rep.addProduct(prod)
    //Função para encontrar o valor mínimo de determinado parâmetro de acordo com determinada condição
    //0-->Max 1-->Min
    def find_values(full_code: String, valores:  List[(String, List[String])], code: String, parameter: String, flag: Int, query: String): (String,String) = {
      val volumePath = getVolumePath
      //val min_find = s"property := #synth EFpmin (loc[Worker] = Work, $parameter);"
      //val max_find = s"property := #synth EFpmax (loc[Worker] = Work, $parameter);"
      val bloco = new StringBuilder
      valores.foreach { case (variavel, lvalores) =>
        val valor = lvalores(0)
        val novoValor = valor match {
          case "false" => "False"
          case "true" => "True"
          case _ => valor
        }

        val modo = lvalores(2)
        val tipo = lvalores(1)
        if modo == "Yes" then bloco.append(s"$variavel\n: parameter;\n") else bloco.append(s"$variavel = $novoValor\n: $tipo;\n")

      }
      val pattern = """\(\*@Limits\*\)\s*((?:.*\S.*\n)+)""".r
      val temp_match = pattern.findAllMatchIn(full_code).toList.map { line => line.toString() }.mkString
      val matches = pattern.findAllMatchIn(full_code).toList.map{ line => line.toString()}.mkString.split("\n").toList
      val cleanedNames = matches.flatMap { line =>
        val beforeColonPattern = """^(.*?)\s*:""".r
        val removeEqualsPattern = """^(.*?)\s*=""".r
        beforeColonPattern.findFirstMatchIn(line).map(_.group(1).trim).map { name =>
          removeEqualsPattern.findFirstMatchIn(name).map(_.group(1).trim).getOrElse(name)
        }
      }

      val cleanedNames_bloco = bloco.mkString.split("\n").toList
      val namePattern = """^([a-zA-Z_][a-zA-Z0-9_]*)\s*[=:]?.*""".r
      val extractedNames = cleanedNames_bloco.flatMap {
        case namePattern(name) => Some(name.trim)
        case _ => None
      }.distinct

      val diff = cleanedNames.diff(extractedNames)
      val bloco_old = new StringBuilder

      diff.foreach{line =>
        val captureUntilSemicolon = s"""(${line}\\s*:\\s*.*?;).*""".r
        val another_regex = s"""(${line}\\s*=\\s*.*?;).*""".r
        val result = captureUntilSemicolon.findFirstMatchIn(temp_match).map(_.group(1)).getOrElse("")
        if result.isEmpty then
          val result_another = another_regex.findFirstMatchIn(temp_match).map(_.group(1)).getOrElse("")
          bloco_old.append(s"""$result_another\n""")
        else
          bloco_old.append(s"""$result\n""")
      }
      bloco.append(bloco_old.mkString)
      println(bloco)
      //println(bloco)
      val updatedCode = full_code.replaceAll("""\(\*@Limits\*\)\s*((?:.*\S.*\n)+)""",s"(*@Limits*)\n"+bloco.toString())
      val newhammerFile = File.createTempFile("queryhammer", ".imi", new File(volumePath))
      val pw = new PrintWriter(newhammerFile)
      pw.write(updatedCode)
      pw.close()
      if flag == 0 then
        val max_find = query
        val max_queryFile = File.createTempFile("query_max", ".imiprop", new File(volumePath))
        val pw = new PrintWriter(max_queryFile)
        pw.write(max_find)
        pw.close()

        val comando1 = "mv imitator /examples"
        val comando2 = "cd /examples/"
        val comando3 = s"./imitator ${newhammerFile.getName} ${max_queryFile.getName}"

        val dockerRunCommand = s"""docker run --rm --entrypoint /usr/bin/bash -v $volumePath:/examples  imitator/imitator -c '$comando1 && $comando2 && $comando3' """
        println(s"Executando comando Docker: $dockerRunCommand")
        val dockerRunOutput = dockerRunCommand.!!
        val resFilePath = s"$volumePath/${newhammerFile.getName.split("\\.").head}.res"
        val resultado = extractLimits(readFile(resFilePath))
        //println(find_limites(resultado))
        //(parameter,find_limites(resultado).toString)
        (parameter,resultado.toString)

      else if flag == 1 then
        val min_find = query
        val min_queryFile = File.createTempFile("query_min", ".imiprop", new File(volumePath))
        val pw = new PrintWriter(min_queryFile)
        pw.write(min_find)
        pw.close()

        val comando1 = "mv imitator /examples"
        val comando2 = "cd /examples/"
        val comando3 = s"./imitator ${newhammerFile.getName} ${min_queryFile.getName}"

        val dockerRunCommand = s"""docker run --rm --entrypoint /usr/bin/bash -v $volumePath:/examples  imitator/imitator -c '$comando1 && $comando2 && $comando3' """
        println(s"Executando comando Docker: $dockerRunCommand")
        val dockerRunOutput = dockerRunCommand.!!
        val resFilePath = s"$volumePath/${newhammerFile.getName.split("\\.").head}.res"
        val resultado = extractLimits(readFile(resFilePath))
        //println(find_limites(resultado))
        //(parameter,find_limites(resultado).toString)
        (parameter,resultado.toString)

      else {
        throw new IllegalArgumentException(s"Invalid input: $flag")
      }
      //("teste","teste")
    }

    def encontrarLinhasComIgual(Code: String): String = {
      val lines = Code.split("\n").toList
      val regex = ".*[^:]\\s*=$".r
      lines.map { line =>
        if (regex.matches(line.trim)) then line.replace("=", "").trim
        else line
      }.mkString("\n")
    }

    def find_limites(limite: String): Int = {
      val number = limite.split("\\D+").filter(_.nonEmpty).mkString.toInt
      if (limite.contains(">")) then
        number + 1
      else if (limite.contains("<")) then
        number - 1
      else if (limite.contains(">=") || limite.contains("<=")) then
        number
      else {
        throw new IllegalArgumentException(s"Invalid input: $limite")
      }
    }


    //Criacao das diretorias /examples para armazenação dos códigos
    def getVolumePath: String = {
      val userHome = System.getProperty("user.home")
      val osName = Properties.osName.toLowerCase
      val path = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples"
      }
      val dirPath = Paths.get(path)
      if !Files.exists(dirPath) then Files.createDirectories(dirPath)
      path
    }

    // Função para verificar se a diretoria é válida
    def isValidPath(path: String): Boolean = Files.exists(Paths.get(path)) && Files.isDirectory(Paths.get(path))

    // Função para iniciar o Docker
    def startDocker(): Unit = {
      val osName = Properties.osName.toLowerCase
      val dockerCommand = osName match {
        case name if name.contains("win") => "\"C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe\""
        case name if name.contains("mac") => "/Applications/Docker.app/Contents/MacOS/Docker"
        case _ => "docker"
      }
      try {
        val startDockerCommand = s"start \"\" $dockerCommand"
        startDockerCommand.!!
        println("Docker iniciado com sucesso.")
      } catch {
        case e: IOException => println(s"Erro ao iniciar o Docker: ${e.getMessage}")
      }
    }

    //Verificar se o Docker está a correr ou não
    def isDockerRunning: Boolean = {
      try {
        val dockerVersionOutput = "docker --version".!!
        println(s"Versão do Docker: $dockerVersionOutput")
        true
      } catch {
        case _: IOException =>
          println("Docker não está em execução.")
          false
      }
    }

    // Criar o ficheiro na pasta volumePath e retorna o mesmo caminho para este ficheiro.imiprop
    def createQueryFile(volumePath: String, c: Configurations): List[(File,String)] = {
      var counter = 1
      val results = ListBuffer[(File,String)]()
      val queries = c.annotations.get("queries")
      val currentDir2 = Paths.get("").toAbsolutePath.resolve("examples")

      val formulas : List[String] = for
        qs <- queries.toList
        line <- qs.attrs.values.toList.sortWith((x, y) => x._1 < y._1)
        comm <- line._2.get(qs.header.indexOf("Formula"))
        comm2 <- line._2.get(qs.header.indexOf("Mode"))
      yield s"property := #$comm2 $comm;"

      val comment : List[String] = for
        qs <- queries.toList
        line <- qs.attrs.values.toList.sortWith((x, y) => x._1 < y._1)
        comentario <- line._2.get((qs.header.indexOf("Comment")))
      yield comentario


      formulas.foreach { formula =>
        //println(formula)
        val updatedFormula = if (formula.contains("Agnot")) then
          formula.replace("Agnot", "AGnot")
        else
          formula
        //val queryFile = File.createTempFile(s"query$counter", ".imiprop", new File(volumePath))
        val queryFile = File.createTempFile(s"query$counter", ".imiprop", new File(currentDir2.toString))
        counter += 1
        val pw = new PrintWriter(queryFile)
        pw.write(updatedFormula)
        pw.close()
        val comentario_final = comment.lift(formulas.indexOf(formula)).get
        val add = (queryFile,comentario_final)
        results += (add)
      }


      results.toList
      //val queryFile = File.createTempFile("query", ".imiprop", new File(volumePath))
      //val pw = new PrintWriter(queryFile)
      //pw.write(query)
      //pw.close()
      //queryFile
    }

    // Função para extrair limites de um relatório
    def extractLimits(report: String): String = {
      val pattern = "(?s)BEGIN CONSTRAINT(.*?)END CONSTRAINT".r
      pattern.findFirstMatchIn(report).map(_.group(1).trim).getOrElse("Não foi corrido a linha com a querie")
    }

    def extractAproximation(report: String): String = {
      val pattern = """Constraint soundness\s*:\s*(\w+)""".r
      pattern.findFirstMatchIn(report) match {
        case Some(m) => m.group(1)
        case None => "Não encontrado"
      }
    }

    //Função para ler um ficheiro
    def readFile(filePath: String): String = {
      val source = Source.fromFile(filePath)
      try source.mkString finally source.close()
    }

    def substitute_code_true(code: String, new_anotations: Annotations.Annotation): String = {
      val bloco = new StringBuilder
      val volumePath = getVolumePath
      val sub = new_anotations.attrs.values.toList.sortWith((x, y) => x._1 < y._1).flatMap {
        case (_, hashmap) =>
          // Verifica se os valores existem antes de acessá-los
          val firstValue = hashmap.get(0)
          val otherValues = List(hashmap.get(1), hashmap.get(2), hashmap.get(5), hashmap.get(3)).flatten // Remove None
          firstValue.map(fv => (fv, otherValues)) // Retorna apenas se `firstValue` existir
      }

      sub.foreach { case (variavel, lvalores) =>
        val valor = lvalores(0)
        val novoValor = valor match {
          case "false" => "False"
          case "true" => "True"
          case _ => valor
        }

        val modo = lvalores(2)
        val modo2 = lvalores(3).contains("?")
        val tipo = lvalores(1)
        if modo2 then bloco.append(s"$variavel\n: parameter;\n") else bloco.append(s"$variavel = $novoValor\n: $tipo;\n")

      }

      val pattern = """\(\*@Limits\*\)\s*((?:.*\S.*\n)+)""".r
      val temp_match = pattern.findAllMatchIn(code).toList.map { line => line.toString() }.mkString
      val matches = pattern.findAllMatchIn(code).toList.map { line => line.toString() }.mkString.split("\n").toList
      val cleanedNames = matches.flatMap { line =>
        val beforeColonPattern = """^(.*?)\s*:""".r
        val removeEqualsPattern = """^(.*?)\s*=""".r
        beforeColonPattern.findFirstMatchIn(line).map(_.group(1).trim).map { name =>
          removeEqualsPattern.findFirstMatchIn(name).map(_.group(1).trim).getOrElse(name)
        }
      }

      val cleanedNames_bloco = bloco.mkString.split("\n").toList
      val namePattern = """^([a-zA-Z_][a-zA-Z0-9_]*)\s*[=:]?.*""".r
      val extractedNames = cleanedNames_bloco.flatMap {
        case namePattern(name) => Some(name.trim)
        case _ => None
      }.distinct

      val diff = cleanedNames.diff(extractedNames)
      val bloco_old = new StringBuilder

      diff.foreach { line =>
        val captureUntilSemicolon = s"""(${line}\\s*:\\s*.*?;).*""".r
        val another_regex = s"""(${line}\\s*=\\s*.*?;).*""".r
        val result = captureUntilSemicolon.findFirstMatchIn(temp_match).map(_.group(1)).getOrElse("")
        if result.isEmpty then
          val result_another = another_regex.findFirstMatchIn(temp_match).map(_.group(1)).getOrElse("")
          bloco_old.append(s"""$result_another\n""")
        else
          bloco_old.append(s"""$result\n""")
      }
      //bloco.append(bloco_old.mkString)
      //println(bloco)
      val updatedCode = code.replaceAll("""\(\*@Limits\*\)\s*((?:.*\S.*\n)+)""", s"(*@Limits*)\n${bloco.toString().split("\n").map(_.trim).mkString("\n")}")
      val newhammerFile = File.createTempFile("hammer_final", ".imi", new File(volumePath))
      val pw = new PrintWriter(newhammerFile)
      pw.write(updatedCode)
      pw.close()
      println(updatedCode)
      newhammerFile.getName
    }


    def substitute_code(code: String, c: Configurations, new_anotations: Annotations.Annotation): String = {
      val path_folder = getVolumePath
      val limites = c.annotations.get("Limits")
      val limites2 = new_anotations
      //N e A ReactTime e sessionTime -- ler os valores e mapear
      val dict = (for {
        qs <- limites.toList
        line <- qs.attrs.values.toList.sortWith((x, y) => x._1 < y._1)
        nome <- line._2.get(qs.header.indexOf("Name"))
        valor <- line._2.get(qs.header.indexOf("Value"))
        modo <- line._2.get(qs.header.indexOf("Parameter"))
        tipo <- line._2.get(qs.header.indexOf("Type"))
        esp <- line._2.get(qs.header.indexOf("Specifications"))
      } yield (nome, List(valor, modo, tipo, esp))).toMap

      var updatedCode = code

      println(dict)
      println(limites)

      val bloco = new StringBuilder
      dict.foreach { case (key, value) =>
        val valor = value.head

        val novoValor = valor match {
          case "false" => "False"
          case "true" => "True"
          case _ => valor
        }
        val modo = value(1)
        val tipo = value(2)
        //code.replace("sessionTime = N", s"sessionTime")
        if modo == "Yes" then bloco.append(s"$key\n: parameter;\n") else bloco.append(s"$key = $novoValor\n: $tipo;\n")

      }

      println(bloco)
      updatedCode = updatedCode.replaceAll("""\(\*@Limits\*\)\s*((?:.*\S.*\n)+)""",s"(*@Limits*)\n"+bloco.toString())

      //Guardar cópia do ficheiro atualizado na pasta examples
      val hammerFile = File.createTempFile("hammer", ".imiprop", new File(path_folder))
      val pw = new PrintWriter(hammerFile)
      pw.write(updatedCode)
      pw.close()

      //retornar o nome do ficheiro
      hammerFile.getName
    }


    //caminho é o ficheiro.imi
    def runImitator(querie: String, caminho: String): (String,String,String) = {
      val currentDir = Paths.get("").toAbsolutePath.resolve("images2")
      val volumePath = getVolumePath
      val currentDir2 = Paths.get("").toAbsolutePath.resolve("examples").toString
      val comando1 = "mv imitator /examples"
      val comando2 = "cd /examples/"
      //val comando3 = s"./imitator $caminho $querie"
      val comando4 = s"./imitator $caminho -imi2PNG"
      val comando3 = if timeout<=0 then
        s"./imitator $caminho $querie"
      else
        s"./imitator $caminho $querie -time-limit $timeout"

      val dockerRunCommand = s"""docker run --rm --entrypoint /usr/bin/bash -v $currentDir2:/examples  imitator/imitator -c '$comando1 && $comando2 && $comando3' """
      val dockerRunCommand_imi = s"""docker run --rm --entrypoint /usr/bin/bash -v $currentDir2:/examples  imitator/imitator -c '$comando1 && $comando2 && $comando4' """

      val logFile = new File("output.log")
      val pw = new PrintWriter(new FileOutputStream(logFile, true))

      def logOutput(line: String): Unit = {
        println(line)
        pw.println(line)
      }


      println(s"Executando comando Docker: $dockerRunCommand")

      //val dockerRunOutput = dockerRunCommand.!!
      val dockerRunOutput: Option[String] = Try(dockerRunCommand.!!) match {
        case Success(output) => Some(output)
        case Failure(ex: RuntimeException) =>
          val errorMessage = ex.getMessage
          if (errorMessage.contains("exit value: 139")) then
            println("Segmentation fault")
            logOutput(s"${ex.getMessage}")
            None
          else {
            println(s"Erro ao executar o comando: $errorMessage")
            logOutput(s"${ex.getMessage}")
            None
          }
        case Failure(ex) =>
          println(s"Erro ao executar o comando Talvez: ${ex.getMessage}")
          logOutput(s"${ex.getMessage}")
          None
      }
      pw.close()
      val dockerRunOutputImi = dockerRunCommand_imi.!!
      println(readFile(logFile.getPath))
      val resFilePath = s"$currentDir2/${caminho.split("\\.").head}.res"
      val imiageFilePath = s"$currentDir2/${caminho.split("\\.").head}-pta.png"
      val aux = new File(imiageFilePath)
      Files.copy(aux.toPath, currentDir.resolve(aux.getName), StandardCopyOption.REPLACE_EXISTING)
      //(extractLimits(readFile(resFilePath)),imiageFilePath,extractAproximation(readFile(resFilePath)))
      (extractLimits(readFile(resFilePath)),currentDir.resolve(aux.getName).toString,extractAproximation(readFile(resFilePath)))
    }

    def main(): Unit = {
      val volumePath = getVolumePath
      val currentDir = Paths.get("").toAbsolutePath.resolve("images2")
      val currentDir2 = Paths.get("").toAbsolutePath.resolve("examples")
      if (!Files.exists(currentDir)) then
        println("A diretoria não existe. Criando a diretoria...")
        Files.createDirectories(currentDir)

      if (!Files.exists(currentDir2)) then
        println("A diretoria não existe. Criando a diretoria...")
        Files.createDirectories(currentDir2)

      //Validações para verficar se o Docker está a correr
      if (!isValidPath(volumePath)) then
        println("A diretoria não existe. Criando a diretoria...")
        Files.createDirectories(Paths.get(volumePath))

      if (!isDockerRunning) then
        startDocker()
        Thread.sleep(20000)
      ////////////////////////////////////////////////////

      //Codigo original sem os valores dos produtos
      val original_hammer_file_old = model.buildOld //Imitator.buildOld(model)
      //Ficheiro original sem os valores dos produtos
      //val originalhammerFile = File.createTempFile("origi_hammer", ".imi", new File(volumePath))
      //val pw = new PrintWriter(originalhammerFile)
      //pw.write(original_hammer_file)
      //pw.close()

      //Nome do ficheiro com as substituições todas feitas
      //val modified_hammer_file = substitute_code(original_hammer_file,confProd)

      //Se nas configurações o user selecionar a funcionalidade do Min ou Max

      val a = confProd.annotations.get("Limits").get.attrs.values.toList.sortWith((x, y) => x._1 < y._1)
      val b = confProd.annotations.get("queries").get.attrs.values.toList.sortWith((x, y) => x._1 < y._1)
      val maybe = confProd.products(prod).toList.map{
        case (nome,_) => nome
      }

      val Limites_Var = confProd.annotations.get("Limits").get.attrs.values.toList.sortWith((x, y) => x._1 < y._1).flatMap {
        case (_, hashmap) =>
          // Verifica se os valores existem antes de acessá-los
          val firstValue = hashmap.get(0)
          val otherValues = List(hashmap.get(3), hashmap.get(2), hashmap.get(5)).flatten // Remove None
          firstValue.map(fv => (fv, otherValues)) // Retorna apenas se `firstValue` existir
      }


      //Ficheiro Imi com as variaveis e parâmetros atualizados
      val modified_hammer_file = encontrarLinhasComIgual(model.buildNew)
      println(modified_hammer_file)
      //val originalFile = File.createTempFile("origi_hammer_new", ".imi", new File(volumePath))
      val originalFile = File.createTempFile("origi_hammer_new", ".imi", new File(currentDir2.toString))
      val pw = new PrintWriter(originalFile)
      pw.write(modified_hammer_file)
      pw.close()
      //Lista com os ficheiros das queries e onde estão armazenados
      val lista_queries = createQueryFile(volumePath,confProd)

      for ((queryFile, queryDesc) <- lista_queries) {
        println(readFile(queryFile.getPath))

        try {
          val res = runImitator(queryFile.getName, originalFile.getName)
          val verificationResult = res._1
          val aprox = res._3

          if (verificationResult.contains("False")) then
            if (aprox == "exact") then
              rep.addFail(s"<em>$queryDesc</em>")
            else
              rep.addFail(s"<em>$queryDesc</em> with aproximation $aprox")
          else if (verificationResult.contains("True")) then
            if (aprox == "exact") then
              rep.addOk(s"<em>$queryDesc</em>")
            else
              rep.addOk(s"<em>$queryDesc</em> with aproximation <strong>$aprox</strong>")
          else
            if (aprox == "exact") then
              rep.addConstrain(s"The property '<em>$queryDesc</em>' is valid for the constraint <strong>$verificationResult</strong>")
            else
              rep.addConstrain(s"The property '<em>$queryDesc</em>' is valid for the constraint <strong>$verificationResult</strong> with aproximation <strong>$aprox</strong>")

          rep.addImage(res._2)
        } catch {
          case e: Exception =>
            println(s"Erro ao executar runImitator para query '$queryDesc': ${e.getMessage}")
            rep.addTO(List(s"Segmentation fault"))
        }
      }





      /*
      if (maybe.contains("Fastest") || maybe.contains("Slowest")) then
        //Encontra os Min ou Max
        //Output --->("Min"/"Max","parameter)
        val foundMin = a.flatMap {
          case (_, hashmap) =>
            hashmap.get(3) match {
              case Some(value) if value.contains("Slowest") =>
                hashmap.get(0).map(v => ("Min", v))
              case Some(value) if value.contains("Fastest") =>
                hashmap.get(0).map(v => ("Max", v))
              case _ => None
            }
        }.distinct

        val sub = a.flatMap {
          case (_, hashmap) =>
            // Verifica se os valores existem antes de acessá-los
            val firstValue = hashmap.get(0)
            val otherValues = List(hashmap.get(1), hashmap.get(2), hashmap.get(5)).flatten // Remove None
            firstValue.map(fv => (fv, otherValues)) // Retorna apenas se `firstValue` existir
        }

        val formulas = b.flatMap {
          case (_, hashmap) =>
            val firstValue = hashmap.get(6)
            val query = s"property := #synth ${hashmap.get(0).get}"
            firstValue.map(fv => (fv, query))
        }.filter(x => foundMin.map(y => y._2).contains(x._1))


        //Retorna os valores para os parâmetros
        //Input exemplo List((Min,totalNails), (Min,m))
        //Output Lista (valor,parameter)
        //val sub_val = foundMin.map{
        //  case ("Min", second) => find_values(original_hammer_file,second,1)
        //  case ("Max", second) => find_values(original_hammer_file,second,0)
        //  case (_, second) => None
        //}

        println(formulas)
        println(foundMin)

        val f_inverted = formulas.map { case (a, b) => (b, a) }
        val l_combined = foundMin ++ f_inverted
        val grouped = l_combined.groupBy(_._2)

        //verificação se cada variavel a ser otimizada tem apenas uma query
        grouped.foreach {
          case (key, values) =>
            if (values.size != 2) then
              throw new IllegalArgumentException(s"Erro: A variavel '$key' tem mais que uma query ou não tem query ou nao foi declarado em Limits")
        }

        val sub_val: List[(String, String)] = foundMin.flatMap {
          case ("Min", second) =>
            val temp1 = grouped.get(second).get.tail.head._1
            println(temp1)
            // Chama find_values e extrai o segundo valor do tuplo
            val result = find_values(original_hammer_file,sub,originalhammerFile.getName, second, 1, temp1)
            List((second, result._2)) // Retorna o tuplo ("Min", valor encontrado)
          case ("Max", second) =>
            val temp2 = grouped.get(second).get.tail.head._1
            println(temp2)
            // Chama find_values e extrai o segundo valor do tuplo
            val result = find_values(original_hammer_file,sub,originalhammerFile.getName, second, 0, temp2)
            List((second, result._2)) // Retorna o tuplo ("Max", valor encontrado)
          case (_, second) =>
            Nil // Não retorna nada para outros casos
        }

        //println(sub_val)

        sub_val.foreach { case (a,b) =>
          println(s"Para Maximizar/Minimizar o parametro $a tem que se respeitar o limite:\n $b ")
        }

        //Alterar a estrutura de dados confprod
        //val new_confProd = confProd.annotations.get("Limits").get.updateField1IfField0EqualsFromTuples(sub_val)
        //println(new_confProd)
        //Criar o novo hammer file com as novas variaveis do new_confProd
        //val modified_hammer_file = substitute_code(original_hammer_file,new_confProd)
      else {
        val new_confProd_1 = confProd.annotations.get("Limits").get
        val modified_hammer_file = substitute_code_true(original_hammer_file, new_confProd_1)
        val lista_queries = createQueryFile(volumePath,confProd)
        //println(lista_queries)
        for (q <- lista_queries) {
          println(q)
          //val res = runImitator(q.getName,modified_hammer_file)
          //println(s"Propriedade($q) é satisfeita para os limites acima de ------> \n $res ")
        }
      }

       */

      //Lista com todas as queries a ser resovidas
      //Ainda vai ser passado o configurations antigo maybe
      //val lista_queries = createQueryFile(volumePath,confProd)

      //Neste passo devemos ter as queries
      //for (q <- lista_queries) {
      //  println(q)
      //  val res = runImitator(q.getName,modified_hammer_file)
      //  println(s"Propriedade($q) é satisfeita para os limites acima de ------> \n $res ")
      //}

    }

    main()
}

