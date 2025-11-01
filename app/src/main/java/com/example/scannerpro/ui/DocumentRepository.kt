

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

@Entity(tableName = "signatures")
data class Signature(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String, // Ruta al archivo .png de la firma
    val createdAt: Long = System.currentTimeMillis()
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

    @Query("UPDATE documents SET name = :newName WHERE id = :documentId")
    suspend fun updateDocumentName(documentId: Long, newName: String)

    @Transaction
    suspend fun mergeDocumentsTransaction(targetDocumentId: Long, sourceDocumentIds: Set<Long>) {
        // Paso 1: Reasigna todas las páginas de los documentos fuente al documento destino
        reassignPages(targetDocumentId, sourceDocumentIds)

        // Paso 2: Borra los documentos fuente, que ahora están vacíos
        deleteDocuments(sourceDocumentIds)
    }

    @Query("UPDATE pages SET documentId = :targetDocumentId WHERE documentId IN (:sourceDocumentIds)")
    suspend fun reassignPages(targetDocumentId: Long, sourceDocumentIds: Set<Long>)

    @Query("DELETE FROM documents WHERE id IN (:documentIds)")
    suspend fun deleteDocuments(documentIds: Set<Long>)

    @Query("DELETE FROM pages WHERE documentId IN (:documentIds)")
    suspend fun deletePagesForDocuments(documentIds: Set<Long>)

    @Transaction
    @Query("SELECT * FROM documents WHERE id IN (:documentIds)")
    suspend fun getDocumentsWithPagesByIds(documentIds: Set<Long>): List<DocumentWithPages>
    @Transaction
    suspend fun deleteDocumentsAndPages(documentIds: Set<Long>) {
        // 1. Borra las páginas (los hijos)
        deletePagesForDocuments(documentIds)
        // 2. Borra los documentos (los padres)
        deleteDocuments(documentIds)
    }


    @Insert
    suspend fun insertSignature(signature: Signature)

    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    suspend fun getAllSignatures(): List<Signature>

    // Eliminar firma guardada ---
    @Delete
    suspend fun deleteSignature(signature: Signature)

}

// --- BASE DE DATOS (ROOM) ---

@Database(entities = [Document::class, Page::class, Signature::class], version = 2)
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
                )
                    .fallbackToDestructiveMigration()
                    .build()
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
    // Dentro de tu clase DocumentRepository
// (Asegúrate de que documentDao sea accesible aquí)

    suspend fun renameDocument(documentId: Long, newName: String) {
        // Llama a la función del DAO (que crearás en el paso 2)
        documentDao.updateDocumentName(documentId, newName)
    }

    // Dentro de tu clase DocumentRepository

    suspend fun mergeDocuments(targetDocumentId: Long, sourceDocumentIds: Set<Long>) {
        // Llama a la función transaccional del DAO (que crearás en el paso 4)
        documentDao.mergeDocumentsTransaction(targetDocumentId, sourceDocumentIds)
    }
    suspend fun deleteDocuments(documentIds: Set<Long>) {
        // 1. Obtener todos los documentos y páginas (esto está bien)
        val documentsToDelete = documentDao.getDocumentsWithPagesByIds(documentIds)

        // 2. Borrar los archivos físicos (dijiste que esto funciona, ¡perfecto!)
        documentsToDelete.forEach { docWithPages ->
            docWithPages.pages.forEach { page ->
                try {
                    val file = File(page.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("DocumentRepository", "Error deleting file: ${page.filePath}", e)
                }
            }
        }

        // --- 3. CORRECCIÓN DE LA LÓGICA DE BORRADO ---
        // En lugar de solo borrar los documentos (que falla),
        // llamamos a una nueva transacción que borra páginas Y documentos.
        documentDao.deleteDocumentsAndPages(documentIds)
    }

    suspend fun createNewDocumentFromCopies(sourceDocumentIds: Set<Long>) {

        // 1. Obtener todos los documentos y páginas a copiar
        val sourceDocuments = documentDao.getDocumentsWithPagesByIds(sourceDocumentIds)

        // 2. Crear un nuevo Documento "padre" en la base de datos
        val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())
        val newDocument = Document(
            name = "Documento Combinado ($timestamp)",
            createdAt = System.currentTimeMillis()
            // Ajusta esto a los campos de tu entidad Document
        )
        // Asumo que tienes una función 'insertDocument' que devuelve el ID
        val newDocumentId = documentDao.insertDocument(newDocument)

        // 3. Iterar, copiar archivos y crear nuevas entidades Page
        var pageIndexCounter = 0
        sourceDocuments.forEach { docWithPages ->
            docWithPages.pages.sortedBy { page: Page -> page.pageNumber }.forEach { page -> // Ordenar por índice

                val oldFile = File(page.filePath)
                if (!oldFile.exists()) {
                    Log.w("Repo", "Archivo fuente no encontrado: ${page.filePath}")
                    return@forEach // Saltar esta página si no existe el archivo
                }

                // Crear un nuevo nombre y ruta de archivo
                // (Asumo que tienes una lógica para crear rutas, la imitaré)
                val newFileName = "MERGED_${newDocumentId}_${pageIndexCounter}_${System.currentTimeMillis()}.png"
                val newFile = File(context.filesDir, "pages/$newFileName") // Ajusta esta ruta
                newFile.parentFile?.mkdirs()

                try {
                    // 4. Copiar el archivo físico
                    oldFile.copyTo(newFile)

                    // 5. Crear la nueva entidad Page en la base de datos
                    val newPage = Page(
                        documentId = newDocumentId,
                        filePath = newFile.absolutePath,
                        pageNumber = pageIndexCounter
                        // Ajusta esto a los campos de tu entidad Page
                    )
                    // Asumo que tienes una función 'insertPage'
                    documentDao.insertPage(newPage)

                    pageIndexCounter++

                } catch (e: Exception) {
                    Log.e("Repo", "Error al copiar archivo: ${e.message}")
                }
            }
        }
    }

    suspend fun insertDocument(document: Document): Long {
        return documentDao.insertDocument(document)
    }

    private fun saveSignatureBitmapToFile(bitmap: Bitmap, fileName: String): String? {
        // Usamos una carpeta separada
        val directory = File(context.filesDir, "signatures")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, fileName)
        return try {
            FileOutputStream(file).use { out ->
                // Usamos PNG para guardar la transparencia de la firma
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    suspend fun saveSignature(signatureBitmap: Bitmap) {
        val fileName = "sig_${System.currentTimeMillis()}.png"
        val filePath = saveSignatureBitmapToFile(signatureBitmap, fileName)

        if (filePath != null) {
            val signature = Signature(filePath = filePath)
            documentDao.insertSignature(signature)
        }
    }

    suspend fun getAllSignatures(): List<Bitmap> {
        return documentDao.getAllSignatures().mapNotNull { signature ->
            try {
                BitmapFactory.decodeFile(signature.filePath)
            } catch (e: Exception) {
                null
            }
        }
    }


    suspend fun deleteSignature(indexToDelete: Int) {
        // 1. Obtenemos la lista de *entidades* de la base de datos
        //    (Es importante llamar al DAO aquí, no al getAllSignatures() de este repo)
        val allSignatures = documentDao.getAllSignatures()

        // 2. Encontramos la entidad específica usando el índice
        val signatureToDelete = allSignatures.getOrNull(indexToDelete)

        if (signatureToDelete == null) {
            Log.w("DocumentRepository", "Índice de firma para borrar no válido: $indexToDelete")
            return
        }

        // 3. Borramos el archivo físico
        try {
            val file = File(signatureToDelete.filePath)
            if (file.exists()) {
                file.delete()
                Log.d("DocumentRepository", "Archivo de firma eliminado: ${signatureToDelete.filePath}")
            }
        } catch (e: Exception) {
            Log.e("DocumentRepository", "Error al eliminar archivo de firma", e)
        }

        // 4. Borramos la entidad de la base de datos
        documentDao.deleteSignature(signatureToDelete)
    }

}

