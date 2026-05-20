package com.almonium.famsubbe

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class FamSubBeApplication

fun main(args: Array<String>) {
    runApplication<FamSubBeApplication>(*args)
}
