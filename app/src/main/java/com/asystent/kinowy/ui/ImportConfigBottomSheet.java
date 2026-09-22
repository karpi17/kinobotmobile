package com.asystent.kinowy.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.asystent.kinowy.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import java.io.InputStream;
import java.time.Year;
import java.util.concurrent.Executors;

/**
 * BottomSheet konfiguracji importu grafiku.
 *
 * Pojawia się PO wybraniu pliku, a PRZED jego parsowaniem.
 * Użytkownik ustawia:
 *  - rok grafiku (domyślnie bieżący rok)
 *  - rolę (do wyodrębnienia własnych zmian)
 *  - tryb importu (aktualizuj / połącz / podgląd)
 *
 * Callback {@link OnParseReadyListener} wywołuje parsowanie z wybranymi opcjami.
 */
public class ImportConfigBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ImportConfigBS";
    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_PREFERRED_ROLE = "preferred_role";
    private static final String PREF_IMPORT_MODE    = "import_mode";  // 0=update, 1=merge, 2=preview

    public interface OnParseReadyListener {
        /**
         * @param uri          wybrany plik
         * @param year         rok grafiku (-1 = auto z pliku)
         * @param role         rola użytkownika (null = automatycznie)
         * @param clearMode    0=aktualizuj, 1=połącz, 2=podgląd
         */
        void onParseReady(Uri uri, int year, @Nullable String role, int clearMode);
    }

    private Uri fileUri;
    private String fileName;
    private OnParseReadyListener listener;

    public static ImportConfigBottomSheet newInstance(Uri fileUri, String fileName) {
        ImportConfigBottomSheet sheet = new ImportConfigBottomSheet();
        sheet.fileUri = fileUri;
        sheet.fileName = fileName;
        return sheet;
    }

    public void setOnParseReadyListener(OnParseReadyListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_import_config, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Nazwa pliku
        TextView tvFilename = view.findViewById(R.id.tv_import_config_filename);
        tvFilename.setText("Plik: " + (fileName != null && !fileName.isEmpty() ? fileName : "—"));

        // Rok — domyślnie bieżący
        TextInputEditText etYear = view.findViewById(R.id.et_import_year);
        etYear.setText(String.valueOf(Year.now().getValue()));

        // Rola — ostatnio używana z prefs
        Spinner spinnerRole = view.findViewById(R.id.spinner_import_role);
        String savedRole = prefs.getString(PREF_PREFERRED_ROLE, "Dowolna (Automatycznie)");
        String[] rolesArray = getResources().getStringArray(R.array.roles_array);
        for (int i = 0; i < rolesArray.length; i++) {
            if (rolesArray[i].equals(savedRole)) {
                spinnerRole.setSelection(i);
                break;
            }
        }

        // Tryb importu — ostatnio używany
        RadioGroup rgMode = view.findViewById(R.id.rg_import_mode);
        int savedMode = prefs.getInt(PREF_IMPORT_MODE, 0);
        if (savedMode == 1) {
            rgMode.check(R.id.rb_mode_merge);
        } else if (savedMode == 2) {
            rgMode.check(R.id.rb_mode_preview);
        } else {
            rgMode.check(R.id.rb_mode_update);
        }

        // Przycisk "Parsuj"
        view.findViewById(R.id.btn_start_parse).setOnClickListener(v -> {
            // Odczytaj rok
            String yearStr = etYear.getText() != null ? etYear.getText().toString().trim() : "";
            int year = -1;
            if (!yearStr.isEmpty()) {
                try {
                    year = Integer.parseInt(yearStr);
                    if (year < 2000 || year > 2100) {
                        Toast.makeText(requireContext(), "Podaj prawidłowy rok (np. 2026)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Nieprawidłowy rok", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Odczytaj rolę
            String selectedRole = spinnerRole.getSelectedItem().toString();
            String roleToPass = selectedRole.startsWith("Dowolna") ? null : selectedRole;

            // Odczytaj tryb
            int checkedId = rgMode.getCheckedRadioButtonId();
            int mode;
            if (checkedId == R.id.rb_mode_merge) {
                mode = 1;
            } else if (checkedId == R.id.rb_mode_preview) {
                mode = 2;
            } else {
                mode = 0;
            }

            // Zapisz preferencje na przyszłość
            prefs.edit()
                    .putString(PREF_PREFERRED_ROLE, selectedRole)
                    .putInt(PREF_IMPORT_MODE, mode)
                    .apply();

            // Wywołaj parsowanie
            if (listener != null) {
                listener.onParseReady(fileUri, year, roleToPass, mode);
            }
            dismiss();
        });

        view.findViewById(R.id.btn_cancel_config).setOnClickListener(v -> dismiss());
    }
}
