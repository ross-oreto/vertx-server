@Grapes([
        @Grab('commons-io:commons-io:2.6'),
        @Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
])
import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseDecorator

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.io.File
import java.io.ByteArrayInputStream
import java.io.Console
import java.io.StringReader
import org.apache.commons.io.IOUtils
import java.util.Base64

Update.updateDir = this.args.length > 0 && this.args[0] != '/' ? '/' + this.args[0] + '/' : '/'
Update.outputDir = this.args.length > 1 ? this.args[1] : '.'
Update.checkAuth()
Update.run()
Update.displayResults()

class Update {
    static String updateDir
    static String outputDir
    static String api = "https://api.github.com"
    static String repoUri = '/repos/ross-oreto/vertx-server'
    static String contentsUri = "$repoUri/contents"
    static String ghJson = 'application/vnd.github.v3+json'
    static String ghRaw = 'application/vnd.github.v3.raw'

    static String rawApi = 'https://rawgit.com'
    static String rawRepo = "$rawApi/ross-oreto/vertx-server/master"

    static String UTF8 = 'utf-8'

    static Map headers = [
            'User-Agent': "Apache HTTPClient"
            , 'Accept':ghJson
    ]

    static Map headersRaw = [
            'User-Agent': "Apache HTTPClient"
    ]

    static RESTClient client = new RESTClient(api)
    static RESTClient rawClient = new RESTClient(rawApi)
    static {
        client.parser.'application/vnd.github.v3.raw' = client.parser.'application/octet-stream'
    }
    static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows")

    static Object getContent(String uri) {
        HttpResponseDecorator response = client.get(['path': "${contentsUri}$uri", 'headers': headers])
        response.data
    }

    static Object getRawContent(String uri) {
        HttpResponseDecorator response = rawClient.get(['path': "${rawRepo}$uri", 'headers': headersRaw, 'contentType': 'text/plain'])
        response.data
    }

    static Object getRawBinary(String uri) {
        HttpResponseDecorator response = rawClient.get(['path': "${rawRepo}$uri", 'headers': headersRaw])
        response.data
    }

    static Console console = System.console()

    static boolean ALL_YES = false
    static boolean ALL_NO = false
    static int updated = 0
    static int created = 0
    static int folders = 0
    static int skipped = 0

    static void checkAuth() {
        File file = new File(Paths.get('.', outputDir, 'auth').toString())
        if (file.exists()) {
            String auth = file.text.trim()
            if (auth.startsWith('Basic'))
                addAuth(auth)
            else {
                addAuth(encodeBasic(auth))
            }
        } else {
            String user = console.readLine("enter user: ")
            String pass = console.readLine("enter pass: ")
            String userpass = "$user:$pass".trim()
            addAuth(encodeBasic(userpass))
            file.write(userpass)
        }
    }

    static String encodeBasic(String basic) {
        'Basic ' + Base64.getEncoder().encodeToString(basic.getBytes(UTF8))
    }

    static run(String uri = '') {
        def content = getContent(uri)
        if(content instanceof Collection) {
            content.each {
                def path = "$uri/${it['name']}"
                def localPath = uriToPath(uri)
                if (!Files.exists(localPath)) {
                    new File(localPath.toString()).mkdir()
                    folders++
                }
                run(path)
            }
        } else if (content.type == 'file') {
            if (uri.startsWith(updateDir)) {
                Path filePath = uriToPath(uri)

                boolean isBinary = false
                def rawContent
                def raw = uri.endsWith('.jar') ||
                        uri.endsWith('.png') ||
                        uri.endsWith('.gif') ||
                        uri.endsWith('.jpg') ? getRawBinary(uri) : getRawContent(uri)
                if (raw instanceof ByteArrayInputStream) {
                    isBinary = true
                    rawContent = new byte[raw.available()]
                    raw.read(rawContent)
                } else if (raw instanceof StringReader) {
                    rawContent = IOUtils.toString(raw)
                } else {
                    rawContent = raw
                }

                if (Files.exists(filePath)) {
                    File file = new File(filePath.toString())
                    def fileBytes
                    if (isBinary) {
                        fileBytes = file.bytes
                    } else {
                        fileBytes = file.getText(UTF8)
                        if (isWindows) {
                            fileBytes = fileBytes.replaceAll('\r\n', '\n')
                        }
                    }
                    if (rawContent != fileBytes) {
                        if (ALL_NO) {
                            skip(filePath)
                        } else if (ALL_YES) {
                            overwrite(filePath, rawContent)
                        } else {
                            String answer = console.readLine("update $filePath? (y)es (n)o (Y)es to all (N)o to all: ")
                            if (answer.startsWith('y')) {
                                overwrite(filePath, rawContent)
                            } else if (answer.startsWith('n')) {
                                skip(filePath)
                            } else if (answer.startsWith('Y')) {
                                overwrite(filePath, rawContent)
                                ALL_YES = true
                            } else if (answer.startsWith('N')) {
                                skip(filePath)
                                ALL_NO = true
                            } else {
                                skip(filePath)
                            }
                        }
                    }
                } else {
                    create(filePath, rawContent)
                }
            }
        }
    }

    static Path uriToPath(String uri) {
        Paths.get('.', outputDir, isWindows ? uri.replaceAll('/', '\\\\') : uri)
    }

    static void overwrite(Path path, def content) {
        println("updating $path")
        Files.write(path, contentToBytes(content))
        updated++
    }

    static void create(Path path, def content) {
        println("creating $path")
        Files.write(path, contentToBytes(content))
        created++
    }

    static byte[] contentToBytes(def content) {
        byte[] bytes
        if (content instanceof byte[]) {
            bytes = content
        } else if (isWindows) {
            bytes = content.replaceAll('\n', '\r\n').getBytes(UTF8)
        } else {
            bytes = content.getBytes(UTF8)
        }
        bytes
    }

    static void skip(Path path) {
        println("skipping $path")
        skipped++
    }

    static void displayResults() {
        println("updated $updated files")
        println("created $created files")
        println("created $folders folders")
        println("skipped $skipped files")
    }

    static AUTH_HEADER = 'Authorization'
    static void addAuth(String auth) {
        headers.put(AUTH_HEADER, auth)
        headersRaw.put(AUTH_HEADER, auth)
    }
}