package com.almonium.famsubbe

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FamSubBeApplication

fun main(args: Array<String>) {
    runApplication<FamSubBeApplication>(*args)
}
