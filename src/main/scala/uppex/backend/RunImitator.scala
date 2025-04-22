/*

object RunImitator:

  def checkProductImitator(prod: String, confProd: Configurations, model: Model, timeout: Int) =
    //println(Imitator.buildNew(model))

    val source = scala.io.Source.fromFile("C:\\Users\\Lenovo\\IdeaProjects\\uppex\\examples\\Teste_novo_excel\\template.txt")
    val template = try source.mkString finally source.close()
    val osName: String = Properties.osName.toLowerCase
    val file = File.createTempFile("uppex", ".imi")
    val pw = new PrintWriter(file)
    //pw.write(Imitator.buildOld(model))
    pw.write(model.buildOld)
    pw.close()
    
    
    def run_query(querie: String, caminho: String): String = {
      val osName = Properties.osName.toLowerCase
      val userHome = System.getProperty("user.home")


      val volumePath_qq = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples"
      }

      val getDesktopPath_antigo = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples"
      }

      val getDesktopPath = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples:/examples"
      }

      //val examplesPath = new File("C:\\Users\\Lenovo\\Desktop\\examples")
      val examplesPath = new File(s"${getDesktopPath}")

      //val file = File.createTempFile("querie", ".imiprop")
      val file = File.createTempFile("querie", ".imiprop", examplesPath)
      val pw = new PrintWriter(file)
      pw.write(querie)
      pw.close()
      val nome = file.getName
      val comando1 = "mv imitator /examples"
      val comando2 = "cd /examples/"
      val comando3 = s"./imitator $caminho $nome"
      //println(comando3)
      print(volumePath_qq)
      val dockerRunCommand1 = s"""docker run --rm --entrypoint /usr/bin/bash -v $volumePath_qq imitator/imitator -c '$comando1 && $comando2 && $comando3' """

      //querie.imiprop
      val dockerRunOutput = dockerRunCommand1.!!
      //ler file .res

      val nomeSemExtensao = nome.substring(0, nome.lastIndexOf("."))
      val resFilePath = osName match {
        case name if name.contains("win") => s"${examplesPath.getAbsolutePath}\\${nomeSemExtensao}.res"
        case _ => s"${examplesPath.getAbsolutePath}/${nomeSemExtensao}.res"
      }
      val limites = extraiLimites(readFile(resFilePath))
      limites
    }

    def process_max_min(nome: String,key: String,especificacoes: String,modo: String,volumePath: String): String = {
      // Função auxiliar para criar o arquivo temporário e escrever a consulta nele
      def createQueryFile(query_temp: String, volumePath: String): File = {
        val queryPath = new File(volumePath)
        val query_file = File.createTempFile("check", ".imiprop", queryPath)
        val query_pw = new PrintWriter(query_file)
        query_pw.write(query_temp)
        query_pw.close()
        query_file
      }

      // Definir a consulta com base no modo
      val query_temp = if (modo.contains("maximize")) then {
        s"property := # synth EFpmax ($especificacoes, $key) ;"
      } else if (modo.contains("minimize")) then {
        s"property := # synth EFpmin ($especificacoes, $key) ;"
      } else {
        throw new IllegalArgumentException("Modo inválido. Use 'maximize' ou 'minimize'.")
      }

      // Criar o arquivo temporário e obter seu nome
      val query_file = createQueryFile(query_temp, volumePath)
      val nomeArquivo = query_file.getName

      // Executar a consulta e retornar o valor atualizado
      val variavel_atualizada = run_query(query_temp, nomeArquivo)
      variavel_atualizada
    }


    def substitute_code(code: String, c: Configurations): String = {
      //val newLine = "\\n".r
      //val anName = """[^*]+""".r
      val limites = confProd.annotations.get("Limits")
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

        //if key == "sessionTime" then
        //  println(s"SessionTime ---> $modo")
        //  println(valor)
        //  updatedCode = if modo == "Yes" then updatedCode.replace("sessionTime_SUB", s"sessionTime") else updatedCode.replace("sessionTime_SUB", s"sessionTime = $valor")
        //  updatedCode = if modo == "Yes" then updatedCode.replaceAll("""N\s""", s""": parameter;\n""") else updatedCode.replace("""N\s""", s": constant;\n")


        //if key == "reactTime" then
        //  println(s"reactTime ---> $modo")
        //  println(valor)
        //  updatedCode = if modo == "Yes" then updatedCode.replaceAll("reactTime_SUB", s"reactTime") else updatedCode.replaceAll("reactTime_SUB", s"reactTime = $valor")
        //  updatedCode = if modo == "Yes" then updatedCode.replaceAll("""A\s""", s": parameter;\n") else updatedCode.replaceAll("""A\s""", s": constant;\n")
      }
      updatedCode = updatedCode.replaceAll("""\(\*@Limits\*\)\s*((?:.*\S.*\n)+)""",s"(*@Limits*)\n"+bloco.toString())
      println(updatedCode)
      val htmlBuilder = new StringBuilder
      val t2 = template.replace("Substituir Aqui!", updatedCode)
      htmlBuilder.append(t2)

      val userHome = System.getProperty("user.home")
      val desktopPath = s"$userHome/Desktop/output_code.html"
      val fileWriter = new PrintWriter(new File(desktopPath))
      fileWriter.write(htmlBuilder.toString)
      fileWriter.close()
      println(s"Arquivo salvo em: $desktopPath")
      val osName = Properties.osName.toLowerCase
      val volumePath = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples"
      }

      //val examplesPath = new File("C:\\Users\\Lenovo\\Desktop\\examples")
      val examplesPath = new File(volumePath)
      val file = File.createTempFile("hammer", ".imi", examplesPath)
      val pw = new PrintWriter(file)
      pw.write(updatedCode)
      pw.close()
      val nome = file.getName

      dict.foreach { case (key, value) =>
        val modo = value(1)
        val especificacoes = value(3)
        if (modo.contains("maximize") || modo.contains("minimize")) then {
          process_max_min(nome,key,especificacoes,modo,volumePath)
        }// Chama a função específica
        val valor = value.head
      
      
      }

      nome
      //updatedCode
    }

    def combineProperties(list1: List[String], list2: List[String]): List[String] = {
      require(list1.length == list2.length, "As listas devem ter o mesmo tamanho")
      val combined = list2.zip(list1).map { case (second, first) =>
        s"property := #$second $first;"
      }
      combined
    }

    def extraiLimites(report: String): String = {
      val pattern = "(?s)BEGIN CONSTRAINT(.*?)END CONSTRAINT".r
      val result = pattern.findFirstMatchIn(report) match {
        case Some(m) => m.group(1).trim
        case None => "Não foi corrido a linha com a querie"
      }
      result
    }

    def readFile(filePath: String): String = {
      val source = Source.fromFile(filePath)
      val content = try source.mkString finally source.close()
      content
    }

    //tirar as queries
    val queries = confProd.annotations.get("queries")


    val formula = for
      qs <- queries.toList
      line <- qs.attrs.values.toList.sortWith((x, y) => x._1 < y._1)
      comm <- line._2.get(qs.header.indexOf("Formula"))
      comm2 <- line._2.get(qs.header.indexOf("Mode"))
    yield s"property := #$comm2 $comm;"

    println(formula)


    val query = formula
    //exemplo
    //val querie = List("property := #synth DeadlockFree;")


    //val iniciardocker = "start \\"\\" \"C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe\\""
    //Comando Antigo
    //val inicairdocker = "\"C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe\""
    //Comando Genérico
    val inicairdocker = osName match {
      case name if name.contains("win") => "\"C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe\""
      case name if name.contains("mac") => "/Applications/Docker.app/Contents/MacOS/Docker"
      case _ => "docker"
    }

    //Comando mantém-se igual em todos os sistemas operativos
    val dockerinstalado = "docker --version"
    val winptyPath = "C:\\Program Files\\Git\\usr\\bin\\winpty.exe"

    //comandos para correr no container UBUNTO
    val comando1 = "mv imitator /examples"
    val comando2 = "cd /examples/"
    val comando3 = s"timeout $timeout ./imitator hammer.imi"
    //val comando3 = "./imitator hammer.imi querie.imiprop"

    //Caminho para a pasta Examples
    val volumePath_antigo = osName match {
      case name if name.contains("win") => "C:\\Users\\Lenovo\\Desktop\\examples"
      case _ => "/path/to/examples"
    }

    val userHome = System.getProperty("user.home")
    val volumePath = osName match {
      case name if name.contains("win") => s"$userHome\\Desktop\\examples:/examples"
      case _ => s"$userHome/Desktop/examples:/examples"
    }

    println(volumePath)

    //parâmetro para configuração
    //Comando Antigo
    //val dockerRunCommand = s"""docker run --rm --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator -c '$comando1 && $comando2 && $comando3' """
    //Comando Genérico
    val dockerRunCommand = s"""docker run --rm --entrypoint /usr/bin/bash -v $volumePath imitator/imitator -c '$comando1 && $comando2 && $comando3' """


    //val dockerRunCommand = s"""docker run --rm --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator -c '$comando1 && $comando2 && $comando3' """


    //val dockerRunCommand = s"""powershell.exe -Command "& { & docker run --rm -it --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator -c '$comando1 && $comando2 && $comando3' }" """  //val dockerRunCommand = """powershell.exe -Command "C:\\Program Files\\Git\\usr\\bin\\winpty.exe docker run --rm -it --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator""""
    //val dockerRunCommand = s"""powershell.exe -Command "& { & '${winptyPath}' docker run --rm --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator -c '$comando1 && $comando2 && $comando3' }" """  //val dockerRunCommand = """powershell.exe -Command "C:\\Program Files\\Git\\usr\\bin\\winpty.exe docker run --rm -it --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator""""
    //val dockerRunCommand = s"""powershell.exe -Command "& { & $winptyPath docker run --rm -it --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator -c '$comando1 && $comando2 && $comando3' }" """

    //Função Genérica para verificar se um determinado caminho é válido
    def isValidPath(path: String): Boolean = {
      val p = Paths.get(path)
      Files.exists(p) && Files.isDirectory(p)
    }

    //Função Genérica para averiguar se o Docker está a correr
    def isDockerRunning: Boolean = {
      try {
        val dockerVersionOutput = dockerinstalado.!!
        println(s"Versão do Docker: $dockerVersionOutput")
        true
      } catch {
        case _: IOException =>
          println("Docker não está em execução.")
          false
      }
    }

    //Função para iniciar o Docker
    def startDocker(): Unit = {
      try {
        val startDockerCommand = s"start \"\" $inicairdocker"
        startDockerCommand.!!
        println("Docker iniciado com sucesso.")
      } catch {
        case e: IOException =>
          println(s"Erro ao iniciar o Docker: ${e.getMessage}")
      }
    }

    //Função para criar diretoria
    def createDirectory(path: String): Boolean = {
      try {
        Files.createDirectories(Paths.get(path))
        println(s"Directory $path created successfully.")
        true
      } catch {
        case e: IOException =>
          println(s"Diretoria já existe $path: ${e.getMessage}")
          false
      }
    }

    def runImitator(querie: String,caminho: String): String = {
      val osName = Properties.osName.toLowerCase
      val userHome = System.getProperty("user.home")

      val getDesktopPath_antigo = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples"
      }

      val getDesktopPath = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples:/examples"
      }

      //val examplesPath = new File("C:\\Users\\Lenovo\\Desktop\\examples")
      val examplesPath = new File(s"${getDesktopPath}")

      //val file = File.createTempFile("querie", ".imiprop")
      val file = File.createTempFile("querie", ".imiprop", examplesPath)
      val pw = new PrintWriter(file)
      pw.write(querie)
      pw.close()
      val nome = file.getName
      val comando1 = "mv imitator /examples"
      val comando2 = "cd /examples/"
      val comando3 = s"./imitator $caminho $nome"
      //println(comando3)
      print(volumePath)
      val dockerRunCommand1 = s"""docker run --rm --entrypoint /usr/bin/bash -v $volumePath:/examples  imitator/imitator -c '$comando1 && $comando2 && $comando3' """
      val dockerRunCommand1_old = s"""docker run --rm --entrypoint /usr/bin/bash -v C:\\Users\\Lenovo\\Desktop\\examples:/examples imitator/imitator -c '$comando1 && $comando2 && $comando3' """
      //querie.imiprop
      val dockerRunOutput = dockerRunCommand1.!!
      //ler file .res
      val limites = extraiLimites(readFile("C:\\Users\\Lenovo\\Desktop\\examples\\hammer.res"))
      limites
    }

    def main(args: List[String]): Unit = {
      val aux = 1
      val osName = Properties.osName.toLowerCase
      val userHome = System.getProperty("user.home")

      val getDesktopPath = osName match {
        case name if name.contains("win") => s"$userHome\\Desktop\\examples"
        case _ => s"$userHome/Desktop/examples"
      }

      val examplesPath = s"${getDesktopPath}"
      //val examplesPath = "C:\\Users\\Lenovo\\Desktop\\examples"


      if (!isValidPath(examplesPath)) then
        println("A diretoria não existe por isso vai se proceder a criação de uma...")
        createDirectory(examplesPath)


      if (!isDockerRunning) then
        startDocker()
        Thread.sleep(20000)

      try {

        val htmlBuilder = new StringBuilder
        htmlBuilder.append("<html><head><title>Resultados</title></head><body>")

        //val dockerVersionOutput = dockerinstalado.!!
        //println(s"Versao do Docker: $dockerVersionOutput")
        val dockerRunOutput = dockerRunCommand.!!
        //println(s"Docker output: $dockerRunOutput")
        if dockerRunOutput.contains("Syntax is correct. Have fun!") then
          println("Syntax do Ficheiro .imi está bem escrita")
        else
          println("Verificar Syntax do Ficheiro .imi")
        val code = substitute_code(model.buildOld,confProd)
        println(code)
        for (q <- query) {
          println(q)
          val resultado = runImitator(q,code)
          htmlBuilder.append(s"<p>Propriedade($q) é satisfeita para os limites acima de ------> <br> $resultado </p>")
          println(s"Propriedade($q) é satisfeita para os limites acima de ------> \n $resultado ")
        }
        println("Docker command ran successfully.")
        htmlBuilder.append("</body></html>")

        val fileWriter = new PrintWriter(new File("output.html"))
        fileWriter.write(htmlBuilder.toString)
        fileWriter.close()

      } catch {
        case e: Exception =>
          println(s"An error occurred: ${e.getMessage}")
      }
    }

    //substitute_code(Imitator.buildOld(model),confProd)
    main(query)

 */




