package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.RateHistory;

import java.util.List;
import java.util.Locale;

/**
 * Adapter dla listy historii stawek godzinowych w ProfileFragment.
 * Każdy element pokazuje datę od kiedy obowiązuje stawka i kwotę.
 * Kliknięcie → edycja stawki.
 * Kliknięcie ikony kosza → usunięcie (potwierdzenie w ProfileFragment).
 */
public class RateHistoryAdapter extends RecyclerView.Adapter<RateHistoryAdapter.VH> {

    public interface OnDeleteClickListener {
        void onDelete(RateHistory rate);
    }

    public interface OnEditClickListener {
        void onEdit(RateHistory rate);
    }

    private final List<RateHistory> rates;
    private final OnDeleteClickListener deleteListener;
    private final OnEditClickListener editListener;

    public RateHistoryAdapter(List<RateHistory> rates,
                              OnDeleteClickListener deleteListener,
                              OnEditClickListener editListener) {
        this.rates = rates;
        this.deleteListener = deleteListener;
        this.editListener = editListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rate_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RateHistory item = rates.get(position);

        holder.tvRate.setText(String.format(Locale.getDefault(), "%.2f zł/h", item.getRate()));
        holder.tvFrom.setText("od " + item.getActiveFrom());

        if (item.getNote() != null && !item.getNote().isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(item.getNote());
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // Kliknięcie w cały element — edycja
        holder.itemView.setOnClickListener(v -> {
            if (editListener != null) editListener.onEdit(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return rates != null ? rates.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvRate, tvFrom, tvNote;
        ImageButton btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvRate    = itemView.findViewById(R.id.tv_rate_value);
            tvFrom    = itemView.findViewById(R.id.tv_rate_from);
            tvNote    = itemView.findViewById(R.id.tv_rate_note);
            btnDelete = itemView.findViewById(R.id.btn_delete_rate);
        }
    }
}
