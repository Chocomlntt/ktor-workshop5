package com.example

import com.example.repositories.BookRepository
import com.example.repositories.LendingRepository
import com.example.routes.bookRoutes
import com.example.routes.lendingRoutes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val defaultBookRepository = BookRepository()
val defaultLendingRepository = LendingRepository()

fun Application.configureRouting(
    bookRepository: BookRepository = defaultBookRepository,
    lendingRepository: LendingRepository = defaultLendingRepository
) {
    routing {
        get("/") {
            call.respondText("Library & Book Lending Management API is running!")
        }
        bookRoutes(bookRepository, lendingRepository)
        lendingRoutes(bookRepository, lendingRepository)
    }
}