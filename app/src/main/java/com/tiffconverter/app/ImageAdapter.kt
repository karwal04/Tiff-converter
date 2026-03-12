package com.tiffconverter.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ImageAdapter(
    private val items: MutableList<Pair<Uri, Boolean>>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.ivPhoto)
        val btn: ImageButton = v.findViewById(R.id.btnRemovePhoto)
        val page: TextView = v.findViewById(R.id.tvPageNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val (uri, isPdf) = items[pos]
        h.page.text = if (isPdf) "📄 PDF ${pos + 1}" else "🖼 Page ${pos + 1}"
        if (isPdf) {
            h.img.setImageResource(android.R.drawable.ic_menu_agenda)
            h.img.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
        } else {
            h.img.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            Glide.with(h.itemView).load(uri).centerCrop().into(h.img)
        }
        h.btn.setOnClickListener { val p = h.adapterPosition; if (p >= 0) onRemove(p) }
    }

    override fun getItemCount() = items.size
}
