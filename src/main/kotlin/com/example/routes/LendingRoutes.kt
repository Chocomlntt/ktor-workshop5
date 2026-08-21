package com.example.routes

import com.example.models.BorrowByBookIdRequest
import com.example.models.MessageResponse
import com.example.models.ReturnRequest
import com.example.repositories.BookRepository
import com.example.repositories.LendingRepository
import com.example.repositories.LendingResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.lendingRoutes(
    bookRepository: BookRepository,
    lendingRepository: LendingRepository
) {
    route("/api/lendings") {
        // GET /api/lendings?bookId=...
        get {
            val bookId = call.request.queryParameters["bookId"]
            val records = lendingRepository.getAll(bookId = bookId)
            call.respond(HttpStatusCode.OK, records)
        }

        // GET /api/lendings/{id}
        get("{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("Missing or invalid record ID")
            )
            val record = lendingRepository.getById(id)
            if (record != null) {
                call.respond(HttpStatusCode.OK, record)
            } else {
                call.respond(HttpStatusCode.NotFound, MessageResponse("Lending record with id '$id' was not found"))
            }
        }

        // POST /api/lendings/borrow (by bookId)
        post("borrow") {
            val request = try {
                call.receive<BorrowByBookIdRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Invalid request body. 'bookId' and 'borrowerName' are required.")
                )
            }

            if (request.bookId.isBlank() || request.borrowerName.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Book ID and borrower name must not be blank")
                )
            }

            when (val result = lendingRepository.borrowBook(request.bookId.trim(), request.borrowerName.trim(), bookRepository)) {
                is LendingResult.Success -> call.respond(HttpStatusCode.OK, result.record)
                is LendingResult.BookNotFound -> call.respond(HttpStatusCode.NotFound, MessageResponse(result.message))
                is LendingResult.BookNotAvailable -> call.respond(HttpStatusCode.Conflict, MessageResponse(result.message))
                is LendingResult.BookNotBorrowed -> call.respond(HttpStatusCode.BadRequest, MessageResponse(result.message))
            }
        }

        // POST /api/lendings/return (by bookId)
        post("return") {
            val request = try {
                call.receive<ReturnRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Invalid request body. 'bookId' is required.")
                )
            }

            if (request.bookId.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Book ID must not be blank")
                )
            }

            when (val result = lendingRepository.returnBook(request.bookId.trim(), bookRepository)) {
                is LendingResult.Success -> call.respond(HttpStatusCode.OK, result.record)
                is LendingResult.BookNotFound -> call.respond(HttpStatusCode.NotFound, MessageResponse(result.message))
                is LendingResult.BookNotBorrowed -> call.respond(HttpStatusCode.BadRequest, MessageResponse(result.message))
                is LendingResult.BookNotAvailable -> call.respond(HttpStatusCode.BadRequest, MessageResponse(result.message))
            }
        }
    }
}
