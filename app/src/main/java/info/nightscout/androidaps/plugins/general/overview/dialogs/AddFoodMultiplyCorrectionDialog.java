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
import android.widget.CompoundButton;

import androidx.fragment.app.DialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.EcarbBolusService;
import info.nightscout.androidaps.plugins.general.food.Food;
import info.nightscout.androidaps.plugins.general.food.FoodService;
import info.nightscout.androidaps.utils.NumberPicker;

public class AddFoodMultiplyCorrectionDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(AddFoodMultiplyCorrectionDialog.class);

    private NumberPicker editCount;
    private NumberPicker editMultiply;

    private Button decrementButton;
    private Button incrementButton;

    //one shot guards
    private boolean okClicked;
    private List<Food> foodListCopy;

    public AddFoodMultiplyCorrectionDialog(List<Food> foodList) {
        this.foodListCopy = FoodService.cloneFoodList(foodList);
    }

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
//            setSummaryText();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_addfood_meal_multiply_correction, container, false);

        view.findViewById(R.id.mdtp_ok).setOnClickListener(this);
        view.findViewById(R.id.mdtp_cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        editMultiply = view.findViewById(R.id.addfood_edit_multiply);
        editMultiply.setParams(1d, 0.1d, 99999d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.mdtp_ok), textWatcher);

        decrementButton = view.findViewById(R.id.overview_addfood_meal_multiply_correction_decrement_button);
        decrementButton.setOnClickListener(this);

        incrementButton = view.findViewById(R.id.overview_addfood_meal_multiply_correction_increment_button);
        incrementButton.setOnClickListener(this);

        editCount = view.findViewById(R.id.addfood_edit_percent);
        editCount.setParams(100d, 30d, 300d, 5d, new DecimalFormat("0"), true, view.findViewById(R.id.mdtp_ok), textWatcher);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);

        //recovering state if there is something
        if (savedInstanceState != null) {
            editCount.setValue(savedInstanceState.getDouble("editCount"));
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle carbsDialogState) {
        carbsDialogState.putDouble("editCount", editCount.getValue());
        super.onSaveInstanceState(carbsDialogState);
    }


    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.mdtp_ok:
                submit();
                break;
            case R.id.mdtp_cancel:
                dismiss();
                break;
            case R.id.overview_addfood_meal_multiply_correction_decrement_button:
                changePickerValue(-1);
                break;
            case R.id.overview_addfood_meal_multiply_correction_increment_button:
                changePickerValue(+1);
                break;
        }
    }

    private void changePickerValue(double changeValue) {
        double oldValue = editMultiply.getValue();
        double newValue = oldValue + changeValue;
        editMultiply.setValue(newValue);
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            double correction = editCount.getValue() / 100;
            for (Food food : foodListCopy) {
                food.correctionFactor = correction;
            }

            setLastFoodList(foodListCopy);
            EcarbBolusService.generateTreatmentWithSummary(getContext(), getFragmentManager(), foodListCopy, true);

            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void setLastFoodList(List<Food> foodList) {
        List<Food> foodListClone = FoodService.cloneFoodList(foodList);
        for (Food food : foodListClone) {
            food.portionCount = food.portionCount / editMultiply.getValue();
        }

        FoodService.getLastFoodList().clear();
        FoodService.setLastFoodList(foodListClone);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
