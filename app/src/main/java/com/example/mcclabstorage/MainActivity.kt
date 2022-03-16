package com.example.mcclabstorage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.ArrayMap
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private val PICK_IMAGE_REQUEST = 78
    private var firebaseStorage: FirebaseStorage? = null
    private var storageReference: StorageReference? = null
    private lateinit var userImage: ImageView
    private var new_image: Boolean = false
    private var image_uri: Uri? = null
    private val userCollectionRef = Firebase.firestore.collection("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initProperties()
        setEventListeners()
    }

    private fun setEventListeners() {
        iv_upload_image.setOnClickListener {
            createDialog()
        }
        btn_save.setOnClickListener {
            saveUserImage()
        }
    }

    private fun saveUserImage() =
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (new_image) {
                    uploadImageToDB(image_uri!!)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Successfully saved", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun uploadImageToDB(filePath: Uri) {
        val ref = storageReference?.child("uploads/" + UUID.randomUUID())
        val uploadTask = ref?.putFile(filePath)

        val urlTask = uploadTask?.continueWithTask(
            Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
                if (!task.isSuccessful) {
                    task.exception?.let {
                        throw it
                    }
                }
                return@Continuation ref.downloadUrl
            }
        )?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUri = task.result
                addUploadRecordToDb(downloadUri.toString())
            } else {
                Toast.makeText(this, "${task.result}", Toast.LENGTH_SHORT).show()
            }
        }?.addOnFailureListener {
            Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose Image")
        builder.setMessage("")
        builder.setPositiveButton("Gallery") { dialogInterface, which ->
            pickImageFromGallery()
        }
        builder.setNeutralButton("Cancel") { dialogInterface, which ->
            dialogInterface.dismiss()
        }
        builder.setNegativeButton("Camera") { dialogInterface, which ->
//            captureImage()
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.show()
    }

    private fun pickImageFromGallery() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Photo"), PICK_IMAGE_REQUEST)
    }

    private fun initProperties() {
        userImage = iv_upload_image
        firebaseStorage = FirebaseStorage.getInstance()
        storageReference = FirebaseStorage.getInstance().reference
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let {
                iv_upload_image.setImageURI(it)
                new_image = true
                image_uri = it
                showSelectedImage(it)
            }
        }
    }

    private fun showSelectedImage(filePath: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, filePath)
            iv_upload_image.setImageBitmap(bitmap)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun addUploadRecordToDb(uri: String) {
        val data = ArrayMap<String, Any>()
        data["user_image"] = uri
        userCollectionRef.add(data)
    }
}
