package com.example.repositories

import com.example.models.LendingRecord
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class LendingResult {
    data class Success(val record: LendingRecord) : LendingResult()
    data class BookNotFound(val message: String = "Book not found") : LendingResult()
    data class BookNotAvailable(val message: String = "Book is already borrowed and not available") : LendingResult()
    data class BookNotBorrowed(val message: String = "Book is not currently borrowed") : LendingResult()
}

class LendingRepository {
    private val records = ConcurrentHashMap<String, LendingRecord>()
    private val lock = Any()

    fun borrowBook(
        bookId: String,
        borrowerName: String,
        bookRepository: BookRepository
    ): LendingResult = synchronized(lock) {
        val book = bookRepository.getById(bookId)
            ?: return LendingResult.BookNotFound("Book with id '$bookId' was not found.")

        if (!book.isAvailable) {
            return LendingResult.BookNotAvailable("Book '${book.title}' (ID: $bookId) is currently borrowed.")
        }

        val recordId = UUID.randomUUID().toString().take(8)
        val now = Instant.now().toString()
        val record = LendingRecord(
            id = recordId,
            bookId = bookId,
            borrowerName = borrowerName,
            checkoutDate = now,
            returnDate = null
        )

        records[recordId] = record
        bookRepository.setAvailability(bookId, false)
        return LendingResult.Success(record)
    }

    fun returnBook(
        bookId: String,
        bookRepository: BookRepository
    ): LendingResult = synchronized(lock) {
        val book = bookRepository.getById(bookId)
            ?: return LendingResult.BookNotFound("Book with id '$bookId' was not found.")

        // Find active lending record where bookId matches and returnDate is null
        val activeRecord = records.values.firstOrNull {
            it.bookId == bookId && it.returnDate == null
        } ?: return LendingResult.BookNotBorrowed("Book '${book.title}' (ID: $bookId) is not currently borrowed.")

        val now = Instant.now().toString()
        val updatedRecord = activeRecord.copy(returnDate = now)
        records[activeRecord.id] = updatedRecord
        bookRepository.setAvailability(bookId, true)

        return LendingResult.Success(updatedRecord)
    }

    fun getAll(bookId: String? = null): List<LendingRecord> {
        return records.values.filter {
            bookId == null || it.bookId == bookId
        }.sortedByDescending { it.checkoutDate }
    }

    fun getById(id: String): LendingRecord? {
        return records[id]
    }

    fun clear() {
        records.clear()
    }
}
