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

Update.checkAuth()
Update.run()
Update.displayResults()

class Update {
    static String api = "https://api.github.com"
    static String repoUri = '/repos/ross-oreto/vertx-server'
    static String contentsUri = "$repoUri/contents"
    static String ghJson = 'application/vnd.github.v3+json'
    static String ghRaw = 'application/vnd.github.v3.raw'

    static String rawApi = 'https://rawgit.com'
    static String rawRepo = "$rawApi/ross-oreto/vertx-server/master"

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
    static int skipped = 0

    static void checkAuth() {
        File file = new File('auth')
        if (file.exists()) {
            addAuth(file.text.trim())
        } else {
            println('authentication not found')
        }
    }

    static run(String uri = '') {
        def content = getContent(uri)
        if(content instanceof Collection) {
            content.each {
                def path = "$uri/${it['name']}"
                run(path)
            }
        } else if (content.type == 'file') {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows")
            String path = isWindows ? uri.replaceAll('/', '\\\\') : uri
            Path filePath = Paths.get('.', path)

            boolean isBinary = false
            def rawContent
            def raw = path.endsWith('.jar') ||
                    path.endsWith('.png') ||
                    path.endsWith('.gif') ||
                    path.endsWith('.jpg') ? getRawBinary(uri) : getRawContent(uri)
            if (raw instanceof ByteArrayInputStream) {
                isBinary = true
                rawContent = new byte[raw.available()]
                raw.read(rawContent)
            } else if (raw instanceof StringReader) {
                rawContent = IOUtils.toString(raw as StringReader)
            } else {
                rawContent = raw
            }

            if (Files.exists(filePath)) {
                File file = new File(filePath.toString())
                def fileBytes
                if (isBinary) {
                    fileBytes = file.bytes
                } else {
                    fileBytes = file.getText('UTF-8')
                    if(isWindows) {
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

    static void overwrite(Path path, def bytes) {
        println("updating $path")
        Files.write(path, bytes instanceof byte[] ? bytes : bytes.bytes)
        updated++
    }

    static void create(Path path, def bytes) {
        println("creating $path")
        Files.write(path, bytes instanceof byte[] ? bytes : bytes.bytes)
        created++
    }

    static void skip(Path path) {
        println("skipping $path")
        skipped++
    }

    static void displayResults() {
        println("updated $updated files")
        println("created $created files")
        println("skipped $skipped files")
    }

    static void addAuth(String auth) {
        headers.put('Authorization', auth)
        headersRaw.put('Authorization', auth)
    }
}