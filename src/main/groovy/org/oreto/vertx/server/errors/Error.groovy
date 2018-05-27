package org.oreto.vertx.server.errors

class Error {
    Integer status
    String message
    Integer code
    String info
}

class Errors {
    List<Error> errors
}
