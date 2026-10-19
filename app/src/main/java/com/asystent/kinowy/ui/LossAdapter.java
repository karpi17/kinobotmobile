package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.Tip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Adapter RecyclerView dla listy transakcji (Straty Loss + Napiwki Tip).
 * Wyświetla elementy {@code item_loss.xml} z kwotą, opisem, datą oraz przyciskiem usuwania.
 */
public class LossAdapter extends RecyclerView.Adapter<LossAdapter.LossViewHolder> {

    public static class TransactionItem {
        public final boolean isTip;
        public final Loss loss;
        public final Tip tip;
        public final String date;
        public final double amount;
        public final String description;

        public TransactionItem(Loss loss) {
            this.isTip = false;
            this.loss = loss;
            this.tip = null;
            this.date = loss.getDate() != null ? loss.getDate() : "";
            this.amount = loss.getAmount();
            this.description = loss.getDescription();
        }

        public TransactionItem(Tip tip) {
            this.isTip = true;
            this.loss = null;
            this.tip = tip;
            this.date = tip.getDate() != null ? tip.getDate() : "";
            this.amount = tip.getAmount();
            this.description = tip.getDescription();
        }

        public int getId() {
            return isTip ? tip.getId() : loss.getId();
        }
    }

    private List<TransactionItem> items = new ArrayList<>();
    private Consumer<Loss> onDeleteLoss;
    private Consumer<Tip> onDeleteTip;

    public LossAdapter() {
    }

    public void setOnDeleteListeners(Consumer<Loss> onDeleteLoss, Consumer<Tip> onDeleteTip) {
        this.onDeleteLoss = onDeleteLoss;
        this.onDeleteTip = onDeleteTip;
    }

    // ─── ViewHolder ──────────────────────────────────────────────────────

    static class LossViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAmount;
        final TextView tvDescription;
        final TextView tvDate;
        final ImageButton btnDelete;

        LossViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAmount = itemView.findViewById(R.id.tv_loss_amount);
            tvDescription = itemView.findViewById(R.id.tv_loss_description);
            tvDate = itemView.findViewById(R.id.tv_loss_date);
            btnDelete = itemView.findViewById(R.id.btn_delete_transaction);
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
        TransactionItem item = items.get(position);

        if (item.isTip) {
            holder.tvAmount.setText(String.format(Locale.getDefault(), "+%.2f zł", item.amount));
            holder.tvAmount.setTextColor(0xFF4CAF50);
        } else {
            holder.tvAmount.setText(String.format(Locale.getDefault(), "-%.2f zł", item.amount));
            holder.tvAmount.setTextColor(0xFFF44336);
        }

        String desc = item.description;
        if (desc != null && !desc.isEmpty()) {
            holder.tvDescription.setText(desc);
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setText(item.isTip ? "Napiwek" : "Strata");
            holder.tvDescription.setVisibility(View.VISIBLE);
        }

        holder.tvDate.setText(formatDate(item.date));

        if (holder.btnDelete != null) {
            if ((item.isTip && onDeleteTip != null) || (!item.isTip && onDeleteLoss != null)) {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setOnClickListener(v -> {
                    if (item.isTip && onDeleteTip != null) {
                        onDeleteTip.accept(item.tip);
                    } else if (!item.isTip && onDeleteLoss != null) {
                        onDeleteLoss.accept(item.loss);
                    }
                });
            } else {
                holder.btnDelete.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─── Aktualizacja danych ─────────────────────────────────────────────

    public void setLosses(List<Loss> newLosses) {
        setTransactions(newLosses, Collections.emptyList());
    }

    public void setTransactions(List<Loss> newLosses, List<Tip> newTips) {
        List<TransactionItem> newItems = new ArrayList<>();
        if (newLosses != null) {
            for (Loss l : newLosses) {
                if (l != null) newItems.add(new TransactionItem(l));
            }
        }
        if (newTips != null) {
            for (Tip t : newTips) {
                if (t != null) newItems.add(new TransactionItem(t));
            }
        }
        newItems.sort((a, b) -> b.date.compareTo(a.date));

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return items.size(); }

            @Override
            public int getNewListSize() { return newItems.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                TransactionItem o = items.get(oldPos);
                TransactionItem n = newItems.get(newPos);
                return o.isTip == n.isTip && o.getId() == n.getId();
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                TransactionItem o = items.get(oldPos);
                TransactionItem n = newItems.get(newPos);
                return o.isTip == n.isTip
                        && safeEquals(o.date, n.date)
                        && Double.compare(o.amount, n.amount) == 0
                        && safeEquals(o.description, n.description);
            }
        });

        this.items = newItems;
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
