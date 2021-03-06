package org.oreto.vertx.server

import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.RESTClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Paths

class ServerSpec extends Specification {
    private static final Logger L = LoggerFactory.getLogger(ServerSpec.class)
    static localhost = 'localhost'
    static ANT = antPath()
    static ENVIRONMENT = System.getProperty('env') ?: (System.getenv().hasProperty('env') ?: 'test')

    @Shared RESTClient client
    @Shared int port

    static String antPath() {
        String path = System.getProperty('ANT_HOME') ?: System.getenv('ANT_HOME')
        String ant = System.getProperty("os.name").toLowerCase().contains("windows") ? 'ant.bat' : 'ant'
        path ? Paths.get(path, 'bin', ant).toAbsolutePath() : ant
    }

    boolean up() {
        try {
            (client.get(['path': '/ping']) as HttpResponseDecorator).status == 200
        } catch (ignored) {
            false
        }
    }

    def setupSpec() {
        port = new File('port').text as int
        L.info(port as String)
        String defaultUrl = "${port == 443 ? 'https' : 'http'}://$localhost:$port/"
        client = new RESTClient(defaultUrl)
        client.ignoreSSLIssues()
        if (!up()) "$ANT -Denv=$ENVIRONMENT start".execute().text
        Thread.sleep(5000)
    }

    def cleanupSpec() {
        "$ANT stop".execute().text
    }

    def "ping"() {
        expect:
            up()
    }
}
