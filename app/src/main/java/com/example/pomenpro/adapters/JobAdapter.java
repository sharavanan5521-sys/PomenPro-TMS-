package com.example.pomenpro.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pomenpro.R;
import com.example.pomenpro.models.Job;
import java.text.SimpleDateFormat;
import java.util.*;

public class JobAdapter extends RecyclerView.Adapter<JobAdapter.VH> {

    public interface OnJobClicked { void onClicked(Job j, int pos); }

    private final Context ctx;
    private final List<Job> data = new ArrayList<>();
    private int selectedPos = RecyclerView.NO_POSITION;
    private final OnJobClicked cb;
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public JobAdapter(Context ctx, OnJobClicked cb) {
        this.ctx = ctx;
        this.cb = cb;
    }

    public void setItems(List<Job> jobs) {
        data.clear();
        data.addAll(jobs);
        selectedPos = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public Job getSelected() {
        return (selectedPos >= 0 && selectedPos < data.size()) ? data.get(selectedPos) : null;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_job, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Job j = data.get(pos);
        h.tvTitle.setText(j.displayId == null ? "(No ID)" : j.displayId);
        h.tvSub.setText(j.serviceType + " • " + j.vehicleNo);
        h.tvNotes.setText(j.notes == null ? "" : j.notes);
        h.tvStatus.setText(j.status == null ? "pending" : j.status);
        h.tvCreatedAt.setText(j.createdAt == 0 ? "" : sdf.format(new Date(j.createdAt)));

        h.card.setCardBackgroundColor(
                selectedPos == pos ? Color.parseColor("#D6F5D6") : Color.WHITE
        );

        h.itemView.setOnClickListener(v -> {
            int old = selectedPos;
            selectedPos = h.getAdapterPosition();
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(selectedPos);
            if (cb != null) cb.onClicked(j, selectedPos);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvTitle, tvSub, tvNotes, tvStatus, tvCreatedAt;
        VH(@NonNull View v) {
            super(v);
            card = (CardView) v;
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSub = v.findViewById(R.id.tvSub);
            tvNotes = v.findViewById(R.id.tvNotes);
            tvStatus = v.findViewById(R.id.tvStatus);
            tvCreatedAt = v.findViewById(R.id.tvCreatedAt);
        }
    }
}
