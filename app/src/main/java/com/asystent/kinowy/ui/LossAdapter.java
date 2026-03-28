package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Loss;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter RecyclerView dla listy strat (Loss).
 * Wyświetla elementy {@code item_loss.xml} z kwotą, opisem i datą.
 */
public class LossAdapter extends RecyclerView.Adapter<LossAdapter.LossViewHolder> {

    private List<Loss> losses = new ArrayList<>();

    // ─── ViewHolder ──────────────────────────────────────────────────────

    static class LossViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAmount;
        final TextView tvDescription;
        final TextView tvDate;

        LossViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAmount = itemView.findViewById(R.id.tv_loss_amount);
            tvDescription = itemView.findViewById(R.id.tv_loss_description);
            tvDate = itemView.findViewById(R.id.tv_loss_date);
        }
    }

    // ─── Adapter methods ─────────────────────────────────────────────────

    @NonNull
    @Override
    public LossViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_loss, parent, false);
        return new LossViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LossViewHolder holder, int position) {
        Loss loss = losses.get(position);

        holder.tvAmount.setText(String.format("-%.2f zł", loss.getAmount()));

        String desc = loss.getDescription();
        if (desc != null && !desc.isEmpty()) {
            holder.tvDescription.setText(desc);
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        holder.tvDate.setText(formatDate(loss.getDate()));
    }

    @Override
    public int getItemCount() {
        return losses.size();
    }

    // ─── Aktualizacja danych ─────────────────────────────────────────────

    public void setLosses(List<Loss> newLosses) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return losses.size(); }

            @Override
            public int getNewListSize() { return newLosses.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return losses.get(oldPos).getId() == newLosses.get(newPos).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Loss o = losses.get(oldPos);
                Loss n = newLosses.get(newPos);
                return o.getDate().equals(n.getDate())
                        && o.getAmount() == n.getAmount()
                        && safeEquals(o.getDescription(), n.getDescription());
            }
        });

        this.losses = new ArrayList<>(newLosses);
        result.dispatchUpdatesTo(this);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return isoDate != null ? isoDate : "";
        try {
            String[] parts = isoDate.split("-");
            return parts[2] + "." + parts[1] + "." + parts[0];
        } catch (Exception e) {
            return isoDate;
        }
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
