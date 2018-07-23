package org.oreto.vertx.server

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.config.ConfigChange
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.impl.CorsHandlerImpl
import org.oreto.vertx.server.errors.Error
import org.oreto.vertx.server.errors.Errors
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Paths

abstract class VertxServer extends AbstractVerticle {
    private static final Logger L = LoggerFactory.getLogger(VertxServer.class)
    static String JSON_CONTENT_TYPE = 'application/json'

    static Map<String, HttpMethod> httpMethodMap = HttpMethod.values().collectEntries {
        [(it.name().toLowerCase()) : it]
    }

    static Collection<HttpMethod> methodsWithBody = [HttpMethod.POST
                                                     , HttpMethod.PUT
                                                     , HttpMethod.DELETE
                                                     , HttpMethod.PATCH
                                                     , HttpMethod.OPTIONS]

    static String JSON_EXT = 'json'
    static String CONF_PATH = 'src/main/conf'

    static Collection<String> CONF_FILE_PATHS = [
            'conf'
            , 'secrets'
            , 'routes'
    ]

    static ENV_PARAM_NAME = 'env'
    static ENV_LOCAL = 'local'
    static ENV_DEV = 'dev'
    static ENVIRONMENT = System.getProperty(ENV_PARAM_NAME) ?: (System.getenv().hasProperty(ENV_PARAM_NAME) ?: ENV_LOCAL)

    static String buildConfPath(String name) { "${CONF_PATH}/${name}.$JSON_EXT" as String }

    static String getConfEnvPath(String path) {
        buildConfPath("$path-${ENVIRONMENT}")
    }

    protected JsonObject config
    protected HttpServer httpServer
    protected Router router
    protected Collection<File> loadedConfigFiles

    @Override
    void start() throws Exception {
        loadedConfigFiles = []
        ConfigRetriever configRetriever = createRetrieverWithSecrets()
        configRetriever.getConfig({ ar ->
            if (ar.failed()) {
                L.error('The config retriever failed, http server will not be started.')
            } else {
                config = ar.result()
                onConfigRetrieved()
                startHttpServer()
                configRetriever.listen(configListener)
            }
        })
    }

    @Override
    void stop() {
        L.info('stopping verticle')
        httpServer?.close({
            L.info('http server closed')
        })
        Files.delete(Paths.get('vid'))
    }

    protected Collection<String> confFilePaths(Collection<String> paths = []) {
        def allPaths = CONF_FILE_PATHS
        allPaths.addAll(paths)
        allPaths
    }

    Map configMapFor(String name) {
        config.getMap().findAll {
            it.key?.startsWith(name)
        }
    }

    Handler<ConfigChange> configListener = { change ->
        L.info('config changed restarting...')
        httpServer?.close({
            L.info('http server closed')
            start()
        })
    }

    protected void onHttpServerStart() {}
    protected void onConfigRetrieved() {}

    void startHttpServer() {
        int port = config.getInteger('port') ?: 9090
        HttpServer httpServer = vertx.createHttpServer()
        Router router = createRoutes()
        httpServer.requestHandler(router.&accept).listen(port, { start ->
            if (start.succeeded()) {
                this.httpServer = start.result()
                onHttpServerStart()
                vertx.fileSystem().writeFile('port', this.httpServer.actualPort().toString() as Buffer, { file ->
                    if (file.failed()) L.warn('port file not created: ' + file.cause()?.message)
                })
                L.info("http server started on port $port")
            } else {
                L.error("http server failed to start: ${start.cause()?.message}")
            }
        })
    }

    static CorsHandler addCors(Route route
                               , String allowedOriginPattern
                               , Set<HttpMethod> allowedMethods
                               , Set<String> allowedHeaders
                               , boolean allowedCredentials) {

        L.info("adding cors handler to route")
        L.info("allowedOriginPattern: $allowedOriginPattern")
        L.info("allowedMethods: $allowedMethods")
        L.info("allowedHeaders: $allowedHeaders")
        L.info("allowedCredentials: $allowedCredentials")
        CorsHandler corsHandler = CorsHandler.create(allowedOriginPattern)
                .allowedHeaders(allowedHeaders)
                .allowedMethods(allowedMethods)
                .allowCredentials(allowedCredentials)
        route.handler(corsHandler)
        CorsHandlerImpl
        corsHandler
    }

    static void addCorsFromConfig(Route route, Map corsConfig) {
        if (corsConfig != null) {
            addCors(route
                    , corsConfig.hasProperty('allowedOriginPattern') ? corsConfig.get('allowedOriginPattern') as String : '*'
                    , (corsConfig.hasProperty('allowedMethods') ? corsConfig.get('allowedMethods') : [HttpMethod.GET
                                                                                                      , HttpMethod.POST
                                                                                                      , HttpMethod.OPTIONS]) as Set<HttpMethod>
                    , (corsConfig.hasProperty('allowedHeaders') ? corsConfig.get('allowedHeaders') : ["x-requested-with"
                                                                                                      , "Access-Control-Allow-Origin"
                                                                                                      , "origin"
                                                                                                      , "Content-Type"
                                                                                                      , "accept"
                                                                                                      , "X-PINGARUNER"]) as Set<String>
                    , corsConfig.hasProperty('allowedCredentials') ? corsConfig.get('allowedCredentials') as Boolean : false)
        }
    }

    Router createRoutes() {
        router = Router.router(vertx)
        addCorsFromConfig(router.route(), config.getJsonObject('routes.cors') as Map)
        try {
            List routes = []
            def routesConfig = configMapFor('routes')
            addCorsFromConfig(router.route(), routesConfig.get('routes.cors') as Map)
            routesConfig.each {
                if(it.value instanceof Collection) routes.addAll(it.value as Collection)
            }
            routes.each {
                JsonObject routeConfig = it as JsonObject
                Route route = router.route()
                String path = routeConfig.getString('path')
                String regex = routeConfig.getString('regex')
                JsonArray method = routeConfig.getJsonArray('method')
                String handlerName = routeConfig.getString('handler')
                JsonArray consumes = routeConfig.getJsonArray('consumes')
                JsonArray produces = routeConfig.getJsonArray('produces')
                Boolean enable = routeConfig.getBoolean('enable') ?: true

                if (path) route.path(path)
                if (regex) route.pathRegex(regex)
                if (method) method.each {
                    def httpMethod = httpMethodMap.get(it as String)
                    route.method(httpMethod)
                    if (methodsWithBody.contains(httpMethod)) {
                        route.handler(BodyHandler.create())
                    }
                }
                if (handlerName) {
                    if (handlerName == 'static') {
                        route.handler(createStaticRoute(routeConfig))
                    } else {
                        route.handler(this."$handlerName" as Handler<RoutingContext>)
                    }
                }
                if (consumes) consumes.each { route.consumes(it as String) }
                if (produces) produces.each { route.produces(it as String) }
                if (enable) route.enable() else route.disable()
                route.failureHandler(defaultFailureHandler)
            }
        } catch (Exception e) {
            L.warn("error creating routes: ${e.message}")
        }
        if (router?.routes?.size()) {
            router?.routes?.each {
                L.info("route: ${it.path}")
            }
        } else {
            L.warn('no routes defined')
        }
        router
    }

    protected StaticHandler createStaticRoute(JsonObject routeConfig) {
        def webRoot = routeConfig.getString('webRoot')
        StaticHandler staticHandler = webRoot ? StaticHandler.create(webRoot) : StaticHandler.create()
        String indexPage = routeConfig.getString('indexPage')
        Boolean dirListing = routeConfig.getBoolean('directoryListing')
        Boolean caching = routeConfig.getBoolean('cachingEnabled')
        if (indexPage) staticHandler.setIndexPage(indexPage)
        if (dirListing != null) staticHandler.setDirectoryListing(dirListing)
        if (caching != null) staticHandler.setCachingEnabled(caching)
        staticHandler
    }

    ConfigRetriever createRetrieverWithSecrets() {
        ConfigRetrieverOptions options = new ConfigRetrieverOptions()

        Collection<String> envPaths = []
        confFilePaths().each {
            envPaths.add(buildConfPath(it))
            envPaths.add(getConfEnvPath(it))
        }

        envPaths.each {
            if (it && vertx.fileSystem().existsBlocking(it)) {
                L.info("loading $it")
                options.addStore(new ConfigStoreOptions().setType('file').setConfig(new JsonObject().put('path', it)))
                loadedConfigFiles.add(new File(it))
            }
        }
        // check if the -conf parameter is passed to the run command
        def confIndex = context.processArgs()?.indexOf('--conf')
        if (confIndex >= 0) {
            String argConf = context.processArgs().get(confIndex + 1)
            if (vertx.fileSystem().existsBlocking(argConf)) {
                L.info("loading $argConf")
                options.addStore(new ConfigStoreOptions().setType('file').setConfig(new JsonObject().put('path', argConf)))
                loadedConfigFiles.add(new File(argConf))
            } else {
                // 3. the -conf param was not a file so just load the raw config
                JsonObject config = context.config()
                if (config) {
                    options.addStore(new ConfigStoreOptions().setType(JSON_EXT).setConfig(config))
                } else {
                    L.warn("could not load specified conf")
                }
            }
        }


        int scanPeriod = ENVIRONMENT == ENV_LOCAL || ENVIRONMENT?.contains(ENV_DEV) ? 5000 : 60000
        // load system and environment variables
        options
                .addStore(new ConfigStoreOptions().setType("sys"))
                .addStore(new ConfigStoreOptions().setType("env"))
                .setScanPeriod(scanPeriod)

        ConfigRetriever.create(vertx, options)
    }

    protected def defaultFailureHandler = { RoutingContext routingContext ->
        handleError(routingContext, routingContext.failure()
                , routingContext.statusCode() > 0 ? routingContext.statusCode() : HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
    }

    protected String extractErrorMessage(Throwable cause, int status) {
        cause?.getMessage() ?: (cause?.class?.name ?: HttpResponseStatus.valueOf(status).reasonPhrase())
    }

    protected void handleError(RoutingContext routingContext, Throwable cause, int status) {
        String message = extractErrorMessage(cause, status)
        L.error(message)
        int code =  -1
        Errors errors = new Errors(errors: [new Error(
                status: status
                , message: message
                , info: code > 0 ? null : 'https://en.wikipedia.org/wiki/List_of_HTTP_status_codes#1xx_Informational_responses'
        )]
        )
        routingContext.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                .setStatusCode(status).end(Json.encode(errors))
    }

    protected void ok(RoutingContext routingContext) {
        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end()
    }

    protected void okJson(RoutingContext routingContext, def response) {
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE).end(response instanceof String ? response : Json.encode(response))
    }

    protected void okText(RoutingContext routingContext, String response) {
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, 'text/plain').end(response)
    }

    protected void redirect(RoutingContext routingContext, String url) {
        routingContext.response().putHeader("location",url).setStatusCode(302).end()
    }

    protected void notFound(RoutingContext routingContext) {
        routingContext.fail(HttpResponseStatus.NOT_FOUND.code())
    }
}
