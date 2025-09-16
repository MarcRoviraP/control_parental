package es.mrp.controlparental

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import es.mrp.controlparental.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardVwChild.setOnClickListener {
            startActivity(android.content.Intent(this, ChildActivity::class.java))
        }
        binding.cardVwParent.setOnClickListener {
            startActivity(android.content.Intent(this, ParentAccountActivity::class.java))
        }
    }
}