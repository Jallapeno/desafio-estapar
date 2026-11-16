package br.com.estapar.parking

import io.micronaut.runtime.Micronaut.run
import java.util.TimeZone

fun configureUtcTimeZone() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
}

fun main(args: Array<String>) {
    configureUtcTimeZone()
    run(*args)
}
