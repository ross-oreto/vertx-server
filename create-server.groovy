@Grapes([
        @Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
])
import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseDecorator
import groovy.lang.GroovyShell

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.io.File

String name = this.args.length > 0 ? this.args[0] : 'my-vertx-server'
String mainVert = this.args.length > 1 ? this.args[1] : 'org.oreto.vertx.server.MyAppServer'

String first = Files.exists(Paths.get('.', 'build.gradle')) ? '..' : '.'
def projectDir = new File(Paths.get(first, name).toString())
if (first =='..') {
    Path authFile = Paths.get('.', 'auth')
    if (Files.exists(authFile)) {
        Files.copy(authFile, Paths.get(first, name, 'auth'))
    }
}
GroovyShell shell = new GroovyShell()
String updateFileName = 'update.groovy'
Path updateFilePath = Paths.get(first, name, updateFileName)
if (projectDir.exists()) {
    println("$name already exists")
    shell.run(new File(updateFilePath.toString()), ['/', projectDir.path])
    createMainVerticle(first, name, mainVert)
} else {
    projectDir.mkdir()
    RESTClient client = new RESTClient('https://api.github.com')
    client.parser.'application/vnd.github.v3.raw' = client.parser.'application/octet-stream'
    HttpResponseDecorator response = client.get(['path': '/repos/ross-oreto/vertx-server/contents/' + updateFileName, 'headers': [
            'User-Agent': "Apache HTTPClient"
            , 'Accept':'application/vnd.github.v3.raw'
    ]])
    def data = response.data
    byte[] bytes = new byte[data.available()]
    data.read(bytes)
    Files.write(updateFilePath, bytes)
    shell.run(new File(updateFilePath.toString()), ['/', projectDir.path])
    createMainVerticle(first, name, mainVert)
}

static void createMainVerticle(String first, String project, String vert) {
    final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows")
    String appPackage = 'org.oreto.vertx.server'
    String appProject = 'vertx-server'

    int endIndex = vert.lastIndexOf('.')
    String vertPackage = vert.substring(0, endIndex)
    String vertClassName = vert.substring(endIndex + 1)
    String appServerName = "${appPackage}.AppServer"

    File buildFile = new File(Paths.get(first, project, 'build.xml').toString())
    buildFile.write(buildFile.text.replace(appServerName, vert).replace(appProject, project))

    buildFile = new File(Paths.get(first, project, 'build.gradle').toString())
    buildFile.write(buildFile.text.replace(appServerName, vert))

    buildFile = new File(Paths.get(first, project, 'settings.gradle').toString())
    buildFile.write(buildFile.text.replace(appProject, project))

    String vertPath = isWindows ? vert.replaceAll('\\.', '\\\\') : vert
    new File(Paths.get(first, project, 'src', 'main', 'groovy'
            , isWindows ? vertPackage.replaceAll('\\.', '\\\\') : vertPackage).toString()).mkdirs()
    File mainVertClass = new File(Paths.get(first, project, 'src', 'main', 'groovy', vertPath + '.groovy').toString())
    if(!mainVertClass.exists()) {
        mainVertClass.write("""package $vertPackage

import ${appPackage}.AppServer

class $vertClassName extends AppServer {
    private static final Logger L = LoggerFactory.getLogger(${vertClassName}.class)
}
""")   
    } else {
        println("${mainVertClass} already exists")
    }
}

