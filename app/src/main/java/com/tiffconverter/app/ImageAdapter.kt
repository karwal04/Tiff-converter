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
    private val images: MutableList<Uri>,
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
        h.page.text = "Page ${pos + 1}"
        Glide.with(h.itemView).load(images[pos]).centerCrop().into(h.img)
        h.btn.setOnClickListener { val p = h.adapterPosition; if (p >= 0) onRemove(p) }
    }

    override fun getItemCount() = images.size
}
