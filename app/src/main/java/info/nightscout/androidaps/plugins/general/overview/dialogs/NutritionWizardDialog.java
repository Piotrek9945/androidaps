package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.fragment.app.DialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Collections;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.EcarbBolusService;
import info.nightscout.androidaps.plugins.general.food.Food;
import info.nightscout.androidaps.plugins.general.food.FoodUtils;
import info.nightscout.androidaps.utils.NumberPicker;

public class NutritionWizardDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(NutritionWizardDialog.class);

    private NumberPicker carbPicker;
    private NumberPicker energyPicker;
    private NumberPicker fatPicker;
    private NumberPicker proteinPicker;
    private NumberPicker portionCountPicker;
    private CheckBox accurateCheckBox;

    private Button decrementButton;
    private Button incrementButton;

    //one shot guards
    private boolean okClicked;

    public NutritionWizardDialog() {}

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_nutrition_wizard_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        carbPicker = view.findViewById(R.id.nutrition_wizard_carbs_input);
        carbPicker.setParams(0d, 0d, 1000d, 1d, new DecimalFormat("0"), true, view.findViewById(R.id.ok), textWatcher);

        energyPicker = view.findViewById(R.id.nutrition_wizard_energy_input);
        energyPicker.setParams(0d, 0d, 1000d, 1d, new DecimalFormat("0"), true, view.findViewById(R.id.ok), textWatcher);

        fatPicker = view.findViewById(R.id.nutrition_wizard_fat_input);
        fatPicker.setParams(0d, 0d, 1000d, 1d, new DecimalFormat("0"), true, view.findViewById(R.id.ok), textWatcher);

        proteinPicker = view.findViewById(R.id.nutrition_wizard_protein_input);
        proteinPicker.setParams(0d, 0d, 1000d, 1d, new DecimalFormat("0"), true, view.findViewById(R.id.ok), textWatcher);

        portionCountPicker = view.findViewById(R.id.nutrition_wizard_portion_count);
        portionCountPicker.setParams(1d, 0.1d, 99999d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok), textWatcher);

        accurateCheckBox = view.findViewById(R.id.nutrition_wizard_accurate);
        accurateCheckBox.setOnClickListener(this);

        decrementButton = view.findViewById(R.id.nutrition_wizard_decrement_button);
        decrementButton.setOnClickListener(this);

        incrementButton = view.findViewById(R.id.nutrition_wizard_increment_button);
        incrementButton.setOnClickListener(this);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);

        return view;
    }

    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                submit();
                break;
            case R.id.cancel:
                dismiss();
                break;
            case R.id.nutrition_wizard_decrement_button:
                changePortionCountValue(-1);
                break;
            case R.id.nutrition_wizard_increment_button:
                changePortionCountValue(+1);
                break;
        }
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            EcarbBolusService.generateTreatmentWithSummary(getContext(), getFragmentManager(), Collections.singletonList(getFood()));
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private Food getFood() {
        int carbs = FoodUtils.Companion.roundDoubleToInt(carbPicker.getValue());
        int energy = FoodUtils.Companion.roundDoubleToInt(energyPicker.getValue());
        int protein = FoodUtils.Companion.roundDoubleToInt(proteinPicker.getValue());
        int fat = FoodUtils.Companion.roundDoubleToInt(fatPicker.getValue());
        double portionCount = portionCountPicker.getValue();
        return Food.createFood(carbs, energy, protein, fat, portionCount, accurateCheckBox.isChecked());
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {}

    private void changePortionCountValue(double changeValue) {
        double oldValue = portionCountPicker.getValue();
        double newValue = oldValue + changeValue;
        portionCountPicker.setValue(newValue);
    }
}
