package com.example.repositories

import com.example.models.Book
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BookRepository {
    private val books = ConcurrentHashMap<String, Book>()
    private val idCounter = AtomicInteger(1)

    fun getAll(
        query: String? = null,
        author: String? = null,
        isAvailable: Boolean? = null
    ): List<Book> {
        return books.values.filter { book ->
            val matchQuery = query.isNullOrBlank() ||
                    book.title.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true)
            val matchAuthor = author.isNullOrBlank() ||
                    book.author.contains(author, ignoreCase = true)
            val matchAvailability = isAvailable == null || book.isAvailable == isAvailable

            matchQuery && matchAuthor && matchAvailability
        }.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
    }

    fun getById(id: String): Book? {
        return books[id]
    }

    fun add(title: String, author: String): Book {
        val id = idCounter.getAndIncrement().toString()
        val book = Book(
            id = id,
            title = title,
            author = author,
            isAvailable = true
        )
        books[id] = book
        return book
    }


    fun addWithId(id: String, title: String, author: String, isAvailable: Boolean = true): Book {
        val book = Book(
            id = id,
            title = title,
            author = author,
            isAvailable = isAvailable
        )
        books[id] = book
        return book
    }

    fun update(
        id: String,
        title: String? = null,
        author: String? = null,
        isAvailable: Boolean? = null
    ): Book? {
        val existing = books[id] ?: return null
        val updated = existing.copy(
            title = title?.ifBlank { existing.title } ?: existing.title,
            author = author?.ifBlank { existing.author } ?: existing.author,
            isAvailable = isAvailable ?: existing.isAvailable
        )
        books[id] = updated
        return updated
    }

    fun delete(id: String): Boolean {
        return books.remove(id) != null
    }

    fun setAvailability(id: String, isAvailable: Boolean): Boolean {
        val existing = books[id] ?: return false
        books[id] = existing.copy(isAvailable = isAvailable)
        return true
    }

    fun clear() {
        books.clear()
        idCounter.set(1)
    }
}

