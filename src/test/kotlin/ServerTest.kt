package com.example

import com.example.models.*
import com.example.repositories.BookRepository
import com.example.repositories.LendingRepository
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ServerTest {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `test root endpoint returns 200`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.status.isSuccess())
    }

    @Test
    fun `test create and get book`() = testApplication {
        val bookRepo = BookRepository()
        val lendingRepo = LendingRepository()

        application {
            configureSerialization()
            configureRouting(bookRepo, lendingRepo)
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        // 1. Create a new book
        val createResponse = testClient.post("/api/books") {
            contentType(ContentType.Application.Json)
            setBody(CreateBookRequest(title = "Kotlin in Action", author = "Dmitry Jemerov"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdBook = createResponse.body<Book>()
        assertNotNull(createdBook.id)
        assertEquals("Kotlin in Action", createdBook.title)
        assertEquals("Dmitry Jemerov", createdBook.author)
        assertTrue(createdBook.isAvailable)

        // 2. Get book by ID
        val getResponse = testClient.get("/api/books/${createdBook.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetchedBook = getResponse.body<Book>()
        assertEquals(createdBook, fetchedBook)
    }

    @Test
    fun `test search and filter books`() = testApplication {
        val bookRepo = BookRepository()
        val lendingRepo = LendingRepository()

        bookRepo.add("Atomic Habits", "James Clear")
        bookRepo.add("Deep Work", "Cal Newport")
        bookRepo.add("Clean Code", "Robert C. Martin")

        application {
            configureSerialization()
            configureRouting(bookRepo, lendingRepo)
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        // Search by query "Atomic"
        val searchResponse = testClient.get("/api/books?query=Atomic")
        assertEquals(HttpStatusCode.OK, searchResponse.status)
        val searchResults = searchResponse.body<List<Book>>()
        assertEquals(1, searchResults.size)
        assertEquals("Atomic Habits", searchResults[0].title)

        // Search by author "Cal"
        val authorResponse = testClient.get("/api/books?author=Cal")
        assertEquals(HttpStatusCode.OK, authorResponse.status)
        val authorResults = authorResponse.body<List<Book>>()
        assertEquals(1, authorResults.size)
        assertEquals("Deep Work", authorResults[0].title)

        // Filter by availability
        val availableResponse = testClient.get("/api/books?isAvailable=true")
        val availableResults = availableResponse.body<List<Book>>()
        assertEquals(3, availableResults.size)
    }

    @Test
    fun `test update and delete book`() = testApplication {
        val bookRepo = BookRepository()
        val lendingRepo = LendingRepository()
        val book = bookRepo.add("Old Title", "Old Author")

        application {
            configureSerialization()
            configureRouting(bookRepo, lendingRepo)
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        // Update book
        val updateResponse = testClient.put("/api/books/${book.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateBookRequest(title = "New Title", author = "New Author"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updatedBook = updateResponse.body<Book>()
        assertEquals("New Title", updatedBook.title)
        assertEquals("New Author", updatedBook.author)

        // Delete book
        val deleteResponse = testClient.delete("/api/books/${book.id}")
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify book is gone
        val getResponse = testClient.get("/api/books/${book.id}")
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `test borrow book by ID updates isAvailable to false and creates lending record`() = testApplication {
        val bookRepo = BookRepository()
        val lendingRepo = LendingRepository()
        val book = bookRepo.add("The Pragmatic Programmer", "Andrew Hunt")

        application {
            configureSerialization()
            configureRouting(bookRepo, lendingRepo)
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        // 1. Borrow book by ID
        val borrowResponse = testClient.post("/api/books/${book.id}/borrow") {
            contentType(ContentType.Application.Json)
            setBody(BorrowRequest(borrowerName = "Alice"))
        }
        assertEquals(HttpStatusCode.OK, borrowResponse.status)
        val lendingRecord = borrowResponse.body<LendingRecord>()
        assertEquals(book.id, lendingRecord.bookId)
        assertEquals("Alice", lendingRecord.borrowerName)
        assertNotNull(lendingRecord.checkoutDate)
        assertNull(lendingRecord.returnDate)

        // 2. Verify book status changed to isAvailable = false
        val bookResponse = testClient.get("/api/books/${book.id}")
        val updatedBook = bookResponse.body<Book>()
        assertFalse(updatedBook.isAvailable)

        // 3. Attempting to borrow again should fail (409 Conflict)
        val secondBorrowResponse = testClient.post("/api/books/${book.id}/borrow") {
            contentType(ContentType.Application.Json)
            setBody(BorrowRequest(borrowerName = "Bob"))
        }
        assertEquals(HttpStatusCode.Conflict, secondBorrowResponse.status)
    }

    @Test
    fun `test return book by ID updates returnDate and isAvailable back to true`() = testApplication {
        val bookRepo = BookRepository()
        val lendingRepo = LendingRepository()
        val book = bookRepo.add("Domain-Driven Design", "Eric Evans")

        application {
            configureSerialization()
            configureRouting(bookRepo, lendingRepo)
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        // 1. Borrow book
        testClient.post("/api/books/${book.id}/borrow") {
            contentType(ContentType.Application.Json)
            setBody(BorrowRequest(borrowerName = "Charlie"))
        }

        // 2. Return book
        val returnResponse = testClient.post("/api/books/${book.id}/return")
        assertEquals(HttpStatusCode.OK, returnResponse.status)
        val returnedRecord = returnResponse.body<LendingRecord>()
        assertEquals(book.id, returnedRecord.bookId)
        assertEquals("Charlie", returnedRecord.borrowerName)
        assertNotNull(returnedRecord.returnDate)

        // 3. Verify book status changed back to isAvailable = true
        val bookResponse = testClient.get("/api/books/${book.id}")
        val updatedBook = bookResponse.body<Book>()
        assertTrue(updatedBook.isAvailable)

        // 4. Returning again when already returned should fail (400 Bad Request)
        val secondReturnResponse = testClient.post("/api/books/${book.id}/return")
        assertEquals(HttpStatusCode.BadRequest, secondReturnResponse.status)
    }

    @Test
    fun `test borrow and return via lending routes by bookId`() = testApplication {
        val bookRepo = BookRepository()
        val lendingRepo = LendingRepository()
        val book = bookRepo.add("Refactoring", "Martin Fowler")

        application {
            configureSerialization()
            configureRouting(bookRepo, lendingRepo)
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        // Borrow via /api/lendings/borrow
        val borrowResponse = testClient.post("/api/lendings/borrow") {
            contentType(ContentType.Application.Json)
            setBody(BorrowByBookIdRequest(bookId = book.id, borrowerName = "David"))
        }
        assertEquals(HttpStatusCode.OK, borrowResponse.status)

        // Verify lending history
        val historyResponse = testClient.get("/api/lendings?bookId=${book.id}")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        val history = historyResponse.body<List<LendingRecord>>()
        assertEquals(1, history.size)
        assertEquals("David", history[0].borrowerName)

        // Return via /api/lendings/return
        val returnResponse = testClient.post("/api/lendings/return") {
            contentType(ContentType.Application.Json)
            setBody(ReturnRequest(bookId = book.id))
        }
        assertEquals(HttpStatusCode.OK, returnResponse.status)
        val returnedRecord = returnResponse.body<LendingRecord>()
        assertNotNull(returnedRecord.returnDate)
    }

    @Test
    fun `test borrow non-existent book returns 404`() = testApplication {
        val bookRepo = BookRepository()
        val lendingRepo = LendingRepository()

        application {
            configureSerialization()
            configureRouting(bookRepo, lendingRepo)
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = testClient.post("/api/books/non-existent-id/borrow") {
            contentType(ContentType.Application.Json)
            setBody(BorrowRequest(borrowerName = "Eve"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
