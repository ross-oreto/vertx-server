package org.oreto.vertx.server

import com.fizzed.rocker.Rocker
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.oreto.vertx.server.logging.LogEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.vertx.core.http.HttpHeaders
import java.nio.charset.StandardCharsets

class AppServer extends VertxServer {
    private static final Logger L = LoggerFactory.getLogger(AppServer.class)
    static String LOG_DIR = 'logs'

    static templateExtension = 'rocker.html'
    static renderTemplate(String template, RoutingContext routingContext, Map<String, Object> model = [:]) {
        String path = template.replaceAll('\\.', '/')
        try {
            def fileName = path.endsWith(".$templateExtension") ? path : "${path}.$templateExtension"
            def rockerTemplate = Rocker.template(fileName)
            model.each { rockerTemplate.bind(it.key, it.value) }
            routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE
                    , "${HttpHeaders.TEXT_HTML}; charset=${StandardCharsets.UTF_8}").end(rockerTemplate.render().toString())
        } catch (Exception e) {
            routingContext.fail(e)
        }
    }

    static Map<String, Level> logLevelMap = [
            'off': Level.OFF
            , 'fatal': Level.FATAL
            , 'error': Level.ERROR
            , 'warn': Level.WARN
            , 'info': Level.INFO
            , 'debug': Level.DEBUG
            , 'trace': Level.TRACE
            , 'all': Level.ALL
    ]

    @Override
    protected void onConfigRetrieved() {
        super.onConfigRetrieved()
        configureLogging()
    }

    protected void configureLogging() {
        String level = config.getString('logLevel')
        if (level) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false)
            Configuration LogConfig = ctx.getConfiguration()
            LoggerConfig loggerConfig = LogConfig.getLoggerConfig(LogManager.ROOT_LOGGER_NAME)
            Level logLevel = logLevelMap.get(level)
            if (logLevel != loggerConfig.level) {
                loggerConfig.setLevel(logLevel)
                ctx.updateLoggers()
            }
        }
    }

    protected File findConfig(String name) {
        if (!name?.endsWith('.json')) name = "${name}.json"
        loadedConfigFiles.find {
            it.name.endsWith(name)
        }
    }

    protected File findLog(String name) {
        if (!name?.endsWith('.log')) name = "${name}.log"
        new File("logs/$name")
    }

    def ping = { RoutingContext routingContext ->
        ok(routingContext)
    }

    def admin = { RoutingContext routingContext ->
        redirect(routingContext, '/admin/logs')
    }

    def conf = { RoutingContext routingContext ->
        List<String> files = loadedConfigFiles.collect {
            String name = it.name
            name.substring(name.lastIndexOf('/') + 1).replace('.json', '')
        }

        renderTemplate('admin.conf', routingContext, [
                'title': 'Application Configuration',
                'files': files
        ])
    }

    def getConf = { RoutingContext routingContext ->
        String name = routingContext.request().getParam("name")
        File file = findConfig(name)
        if (file && file.exists()) {
            okJson(routingContext
                    , file.text.replaceAll('\n', '').replaceAll('\r', ''))
        } else routingContext.fail(HttpResponseStatus.NOT_FOUND.code())
    }

    def saveConf = { RoutingContext routingContext ->
        String name = routingContext.request().getParam("name")
        File file = findConfig(name)
        String body = routingContext.bodyAsString
        try { routingContext.bodyAsJson }
        catch (Exception e) { body = null }
        if (body) {
            if (file && file.exists()) {
                vertx.fileSystem().writeFileBlocking(file.path, body as Buffer)
                okJson(routingContext, file.text)
            } else {
                routingContext.fail(HttpResponseStatus.NOT_FOUND.code())
            }
        } else {
            routingContext.fail(HttpResponseStatus.BAD_REQUEST.code())
        }
    }

    def logs = { RoutingContext routingContext ->
        vertx.fileSystem().readDir(LOG_DIR, { files ->
            def logFiles = []
            if (files.succeeded()) {
                def sep = File.separator
                logFiles = files.result().collect {
                    it.substring(it.lastIndexOf(sep) + 1)
                }
            } else {
                L.warn("Could not read directory $LOG_DIR: ${files.cause().message}")
            }
            renderTemplate('admin.log', routingContext, [
                    'title': 'Server logs',
                    'files': logFiles
            ])
        })
    }

    def getLog = { RoutingContext routingContext ->
        String accept = routingContext.acceptableContentType
        File file = findLog(routingContext.request().getParam("name"))

        if (file?.exists()) {
            vertx.fileSystem().readFile(file.path, { log ->
                if (log.succeeded()) {
                    if (accept.contains('json')) {
                        String text = log.result().toString()
                        Collection<Integer> entries = []
                        int i = 0
                        (text =~ /\d\d \w\w\w \d\d\d\d \d\d:\d\d:\d\d,\d\d\d (AM|PM) \[[a-zA-Z0-9| |\-|_|\.]+\]/).each {
                            i = text.indexOf(it[0] as String, i)
                            entries.add(i++)
                        }
                        def len = entries.size()
                        i = 0
                        List<LogEntry> logEntries = []
                        entries.each {
                            def entry
                            if (i == len - 1) {
                                entry = text.substring(it).trim()
                            } else {
                                entry = text.substring(it, entries[i+1]).trim()
                            }
                            i++
                            if(entry) {
                                logEntries.add(LogEntry.New(entry, i))
                            }
                        }
                        okJson(routingContext, logEntries)
                    } else {
                        okText(routingContext, log.result() as String)
                    }
                } else {
                    routingContext.fail(log.cause())
                }
            })
        } else {
            routingContext.fail(HttpResponseStatus.NOT_FOUND.code())
        }
    }

    def deleteLog = { RoutingContext routingContext ->
        File file = findLog(routingContext.request().getParam("name"))
        if (file?.exists()) {
            vertx.fileSystem().delete(file.path, { delete ->
                if (delete.succeeded()) {
                    ok(routingContext)
                } else {
                    routingContext.fail(delete.cause())
                }
            })
        } else {
            routingContext.fail(HttpResponseStatus.NOT_FOUND.code())
        }
    }
}
