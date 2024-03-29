package com.gomezjair.cazarpatosgmezjair

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.*


class MainActivity : AppCompatActivity() {
    lateinit var textViewUsuario: TextView
    lateinit var textViewContador: TextView
    lateinit var textViewTiempo: TextView
    lateinit var imageViewPato: ImageView
    var contador = 0
    var anchoPantalla = 0
    var alturaPantalla = 0
    var gameOver = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Inicialización de variables
        // encontrar los ids del xml
        textViewUsuario = findViewById(R.id.textViewUsuario)
        textViewContador = findViewById(R.id.textViewContador)
        textViewTiempo = findViewById(R.id.textViewTiempo)
        imageViewPato = findViewById(R.id.imageViewPato)

        //Obtener el usuario de pantalla login
        val extras = intent.extras ?: return
        var usuario = extras.getString(EXTRA_LOGIN) ?:"Unknown"
        val split = usuario.split("@").toTypedArray()
        usuario = split[0]
        textViewUsuario.setText(usuario)
        setSupportActionBar(findViewById(R.id.my_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        //Determina el ancho y largo de pantalla
        inicializarPantalla()
        //Cuenta regresiva del juego
        inicializarCuentaRegresiva()

        //Evento clic sobre la imagen del pato
        imageViewPato.setOnClickListener {
            if (gameOver) return@setOnClickListener
            contador++
            MediaPlayer.create(this, R.raw.gunshot).start()
            textViewContador.setText(contador.toString())
            imageViewPato.setImageResource(R.drawable.duck_clicked)
            //Evento que se ejecuta luego de 500 milisegundos
            Handler().postDelayed(Runnable {
                imageViewPato.setImageResource(R.drawable.duck)
                moverPato()
            }, 500)

        }
    }
    fun jugarOnline(){
        var intentWeb = Intent()
        intentWeb.action = Intent.ACTION_VIEW
        intentWeb.data = Uri.parse("https://duckhuntjs.com/")
        startActivity(intentWeb)
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu,menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_nuevo_juego -> {
                reiniciarJuego()
                true
            }
            R.id.action_jugar_online -> {
                jugarOnline()
                true
            }
            R.id.action_ranking -> {
                val intent = Intent(this, RankingActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }




    private fun inicializarPantalla() {
        // 1. Obtenemos el tamaño de la pantalla del dispositivo
        val display = this.resources.displayMetrics
        anchoPantalla = display.widthPixels
        alturaPantalla = display.heightPixels
    }

    private fun moverPato() {
        val min = imageViewPato.getWidth()/2
        val maximoX = anchoPantalla - imageViewPato.getWidth()
        val maximoY = alturaPantalla - imageViewPato.getHeight()
        // Generamos 2 números aleatorios, para la coordenadas x , y
        val randomX = Random().nextInt(maximoX - min ) + 1
        val randomY = Random().nextInt(maximoY - min ) + 1

        // Utilizamos los números aleatorios para mover el pato a esa nueva posición
        imageViewPato.setX(randomX.toFloat())
        imageViewPato.setY(randomY.toFloat())
    }
    var contadorTiempo = object : CountDownTimer(10000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val segundosRestantes = millisUntilFinished / 1000
            textViewTiempo.setText("${segundosRestantes}s")
        }
        override fun onFinish() {
            textViewTiempo.setText("0s")
            gameOver = true
            mostrarDialogoGameOver()
            val nombreJugador = textViewUsuario.text.toString()
            val patosCazados = textViewContador.text.toString()
            procesarPuntajePatosCazados(nombreJugador, patosCazados.toInt())

        }
    }

    private fun inicializarCuentaRegresiva() {
        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val segundosRestantes = millisUntilFinished / 1000
                textViewTiempo.setText("${segundosRestantes}s")
            }
            override fun onFinish() {
                textViewTiempo.setText("0s")
                gameOver = true
                mostrarDialogoGameOver()
                val nombreJugador = textViewUsuario.text.toString()
                val patosCazados = textViewContador.text.toString()
                procesarPuntajePatosCazados(nombreJugador, patosCazados.toInt())
            }
        }.start()
    }

    private fun mostrarDialogoGameOver() {
        val builder = AlertDialog.Builder(this)
        builder
            .setMessage("Felicidades!!\nHas conseguido cazar $contador patos")
            .setTitle("Fin del juego")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Reiniciar",
                { _, _ ->
                    reiniciarJuego()
                })
            .setNegativeButton("Cerrar",
                { _, _ ->
                    //dialog.dismiss()
                })
        builder.create().show()
    }
    fun procesarPuntajePatosCazados(nombreJugador:String, patosCazados:Int){
        val jugador = Jugador(nombreJugador,patosCazados)
        //Trata de obtener id del documento del ranking específico,
        // si lo obtiene lo actualiza, caso contrario lo crea
        val db = Firebase.firestore
        db.collection("ranking")
            .whereEqualTo("usuario", jugador.usuario)
            .get()
            .addOnSuccessListener { documents ->
                if(documents!= null &&
                    documents.documents != null &&
                    documents.documents.count()>0
                ){
                    val idDocumento = documents.documents.get(0).id
                    actualizarPuntajeJugador(idDocumento, jugador)
                }
                else{
                    ingresarPuntajeJugador(jugador)
                }
            }
            .addOnFailureListener { exception ->
                Log.w(EXTRA_LOGIN, "Error getting documents", exception)
                Toast.makeText(this, "Error al obtener datos de jugador", Toast.LENGTH_LONG).show()
            }
    }
    fun ingresarPuntajeJugador(jugador:Jugador){
        val db = Firebase.firestore
        db.collection("ranking")
            .add(jugador)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this,"Puntaje usuario ingresado exitosamente", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { exception ->
                Log.w(EXTRA_LOGIN, "Error adding document", exception)
                Toast.makeText(this,"Error al ingresar el puntaje", Toast.LENGTH_LONG).show()
            }
    }
    fun actualizarPuntajeJugador(idDocumento:String, jugador:Jugador){
        val db = Firebase.firestore
        db.collection("ranking")
            .document(idDocumento)
            //.update(contactoHashMap)
            .set(jugador) //otra forma de actualizar
            .addOnSuccessListener {
                Toast.makeText(this,"Puntaje de usuario actualizado exitosamente", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { exception ->
                Log.w(EXTRA_LOGIN, "Error updating document", exception)
                Toast.makeText(this,"Error al actualizar el puntaje" , Toast.LENGTH_LONG).show()
            }
    }
    fun eliminarPuntajeJugador(idDocumentoSeleccionado:String){
        val db = Firebase.firestore
        db.collection("ranking")
            .document(idDocumentoSeleccionado)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this,"Puntaje de usuario eliminado exitosamente", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { exception ->
                Log.w(EXTRA_LOGIN, "Error deleting document", exception)
                Toast.makeText(this,"Error al eliminar el puntaje" , Toast.LENGTH_LONG).show()
            }
    }


    fun reiniciarJuego(){
        contador = 0
        gameOver = false
        contadorTiempo.cancel()
        textViewContador.setText(contador.toString())
        moverPato()
        inicializarCuentaRegresiva()
    }


}

