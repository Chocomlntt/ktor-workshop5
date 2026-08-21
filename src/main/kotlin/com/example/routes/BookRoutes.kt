package com.example.routes

import com.example.models.BorrowRequest
import com.example.models.CreateBookRequest
import com.example.models.MessageResponse
import com.example.models.UpdateBookRequest
import com.example.repositories.BookRepository
import com.example.repositories.LendingRepository
import com.example.repositories.LendingResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.bookRoutes(
    bookRepository: BookRepository,
    lendingRepository: LendingRepository
) {
    route("/api/books") {
        // GET /api/books?query=...&author=...&isAvailable=true
        get {
            val query = call.request.queryParameters["query"]
            val author = call.request.queryParameters["author"]
            val isAvailable = call.request.queryParameters["isAvailable"]?.toBooleanStrictOrNull()
                ?: call.request.queryParameters["available"]?.toBooleanStrictOrNull()

            val books = bookRepository.getAll(
                query = query,
                author = author,
                isAvailable = isAvailable
            )
            call.respond(HttpStatusCode.OK, books)
        }

        // GET /api/books/{id}
        get("{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("Missing or invalid book ID")
            )
            val book = bookRepository.getById(id)
            if (book != null) {
                call.respond(HttpStatusCode.OK, book)
            } else {
                call.respond(HttpStatusCode.NotFound, MessageResponse("Book with id '$id' was not found"))
            }
        }

        // POST /api/books
        post {
            val request = try {
                call.receive<CreateBookRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Invalid request body: ${e.localizedMessage}")
                )
            }

            if (request.title.isBlank() || request.author.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Title and author must not be blank")
                )
            }

            val created = bookRepository.add(
                title = request.title.trim(),
                author = request.author.trim()
            )
            call.respond(HttpStatusCode.Created, created)
        }

        // PUT /api/books/{id}
        put("{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("Missing or invalid book ID")
            )

            val request = try {
                call.receive<UpdateBookRequest>()
            } catch (e: Exception) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Invalid request body: ${e.localizedMessage}")
                )
            }

            val updated = bookRepository.update(
                id = id,
                title = request.title?.trim(),
                author = request.author?.trim(),
                isAvailable = request.isAvailable
            )

            if (updated != null) {
                call.respond(HttpStatusCode.OK, updated)
            } else {
                call.respond(HttpStatusCode.NotFound, MessageResponse("Book with id '$id' was not found"))
            }
        }

        // DELETE /api/books/{id}
        delete("{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("Missing or invalid book ID")
            )

            val deleted = bookRepository.delete(id)
            if (deleted) {
                call.respond(HttpStatusCode.OK, MessageResponse("Book with id '$id' deleted successfully"))
            } else {
                call.respond(HttpStatusCode.NotFound, MessageResponse("Book with id '$id' was not found"))
            }
        }

        // POST /api/books/{id}/borrow
        post("{id}/borrow") {
            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("Missing or invalid book ID")
            )

            val request = try {
                call.receive<BorrowRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Invalid request body. 'borrowerName' is required.")
                )
            }

            if (request.borrowerName.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("Borrower name must not be blank")
                )
            }

            when (val result = lendingRepository.borrowBook(id, request.borrowerName.trim(), bookRepository)) {
                is LendingResult.Success -> call.respond(HttpStatusCode.OK, result.record)
                is LendingResult.BookNotFound -> call.respond(HttpStatusCode.NotFound, MessageResponse(result.message))
                is LendingResult.BookNotAvailable -> call.respond(HttpStatusCode.Conflict, MessageResponse(result.message))
                is LendingResult.BookNotBorrowed -> call.respond(HttpStatusCode.BadRequest, MessageResponse(result.message))
            }
        }

        // POST /api/books/{id}/return
        post("{id}/return") {
            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("Missing or invalid book ID")
            )

            when (val result = lendingRepository.returnBook(id, bookRepository)) {
                is LendingResult.Success -> call.respond(HttpStatusCode.OK, result.record)
                is LendingResult.BookNotFound -> call.respond(HttpStatusCode.NotFound, MessageResponse(result.message))
                is LendingResult.BookNotBorrowed -> call.respond(HttpStatusCode.BadRequest, MessageResponse(result.message))
                is LendingResult.BookNotAvailable -> call.respond(HttpStatusCode.BadRequest, MessageResponse(result.message))
            }
        }
    }
}
