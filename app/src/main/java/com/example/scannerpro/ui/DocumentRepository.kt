import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- ENTIDADES DE LA BASE DE DATOS ---

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = Document::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val filePath: String,
    val pageNumber: Int
)

// Clase de relación para obtener un documento con todas sus páginas
data class DocumentWithPages(
    @Embedded val document: Document,
    @Relation(parentColumn = "id", entityColumn = "documentId")
    val pages: List<Page>
)

// --- DAO (Data Access Object) ---

@Dao
interface DocumentDao {
    @Insert
    suspend fun insertDocument(document: Document): Long

    @Insert
    suspend fun insertPage(page: Page)

    @Update
    suspend fun updatePage(page: Page)

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getPagesForDocument(documentId: Long): List<Page>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsWithPages(): List<DocumentWithPages>
}

// --- BASE DE DATOS (ROOM) ---

@Database(entities = [Document::class, Page::class], version = 1)
abstract class DocumentDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: DocumentDatabase? = null

        fun getDatabase(context: Context): DocumentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DocumentDatabase::class.java,
                    "document_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- REPOSITORIO ---

class DocumentRepository(private val context: Context, private val documentDao: DocumentDao) {

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): String? {
        val directory = File(context.filesDir, "scans")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, fileName)
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createDocumentAndAddFirstPage(firstPageBitmap: Bitmap): Long {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val document = Document(name = "Scan $timestamp")
        val documentId = documentDao.insertDocument(document)

        addPageToDocument(documentId, firstPageBitmap)
        return documentId
    }

    suspend fun addPageToDocument(documentId: Long, pageBitmap: Bitmap) {
        val currentPageCount = documentDao.getPagesForDocument(documentId).size
        val fileName = "scan_${documentId}_${currentPageCount + 1}.jpg"
        val filePath = saveBitmapToFile(pageBitmap, fileName)
        if (filePath != null) {
            val page = Page(documentId = documentId, filePath = filePath, pageNumber = currentPageCount + 1)
            documentDao.insertPage(page)
        }
    }

    suspend fun updatePageInDocument(documentId: Long, pageIndex: Int, pageBitmap: Bitmap) {
        val pages = documentDao.getPagesForDocument(documentId)
        if (pageIndex < pages.size) {
            val pageToUpdate = pages[pageIndex]
            // Sobrescribe el archivo existente
            saveBitmapToFile(pageBitmap, File(pageToUpdate.filePath).name)
        }
    }

    suspend fun getDocumentPages(documentId: Long): List<Bitmap> {
        return documentDao.getPagesForDocument(documentId).mapNotNull { page ->
            try {
                BitmapFactory.decodeFile(page.filePath)
            } catch (e: Exception) {
                null
            }
        }
    }

    // FUNCIÓN AÑADIDA QUE FALTABA
    suspend fun getAllDocumentsWithPages(): List<DocumentWithPages> {
        return documentDao.getAllDocumentsWithPages()
    }
}

