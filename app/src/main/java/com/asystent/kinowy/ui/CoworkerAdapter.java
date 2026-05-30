package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.GlobalShift;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter RecyclerView do wyświetlania edytowalnej listy współpracowników
 * w dialogu edycji zmiany.
 * <p>
 * Kliknięcie w element wywołuje {@link OnCoworkerClickListener#onCoworkerClick(GlobalShift, int)}.
 */
public class CoworkerAdapter extends RecyclerView.Adapter<CoworkerAdapter.ViewHolder> {

    public interface OnCoworkerClickListener {
        void onCoworkerClick(GlobalShift globalShift, int position);
    }

    private final List<GlobalShift> coworkers = new ArrayList<>();
    private final OnCoworkerClickListener listener;

    public CoworkerAdapter(OnCoworkerClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<GlobalShift> data) {
        coworkers.clear();
        if (data != null) {
            for (GlobalShift gs : data) {
                if (gs.getStartTime() != null && !gs.getStartTime().trim().isEmpty()) {
                    coworkers.add(gs);
                }
            }
        }
        notifyDataSetChanged();
    }
    public void removeAt(int position) {
        if (position >= 0 && position < coworkers.size()) {
            coworkers.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateAt(int position, GlobalShift updated) {
        if (position >= 0 && position < coworkers.size()) {
            coworkers.set(position, updated);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coworker_edit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GlobalShift gs = coworkers.get(position);
        holder.tvName.setText(gs.getName());
        holder.tvTime.setText(gs.getStartTime() + "–" + gs.getEndTime());
        holder.tvCategory.setText(gs.getCategory() != null ? gs.getCategory() : "?");

        // Pokaż ikonkę ✏️ dla ręcznie edytowanych
        if (holder.tvManualIcon != null) {
            holder.tvManualIcon.setVisibility(gs.isManuallyEdited() ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCoworkerClick(gs, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return coworkers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvManualIcon, tvName, tvTime, tvCategory;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvManualIcon = itemView.findViewById(R.id.tv_coworker_manual_icon);
            tvName = itemView.findViewById(R.id.tv_coworker_name);
            tvTime = itemView.findViewById(R.id.tv_coworker_time);
            tvCategory = itemView.findViewById(R.id.tv_coworker_category);
        }
    }
}
