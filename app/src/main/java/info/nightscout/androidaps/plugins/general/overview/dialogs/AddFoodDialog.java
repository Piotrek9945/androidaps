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
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Collections;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.EcarbBolusService;
import info.nightscout.androidaps.plugins.general.food.Food;
import info.nightscout.androidaps.plugins.general.food.FoodService;
import info.nightscout.androidaps.utils.NumberPicker;

public class AddFoodDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(AddFoodDialog.class);

    private final Food food;
    private final boolean isLastMeal;

    private NumberPicker editCount;
    private Button floatDecrementButton;
    private Button floatIncrementButton;
    private TextView lastMealText;

    //one shot guards
    private boolean okClicked;

    public AddFoodDialog(Food food, boolean isLastMeal) {
        this.food = food;
        this.isLastMeal = isLastMeal;
    }

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
        View view = inflater.inflate(R.layout.overview_addfood_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        editCount = view.findViewById(R.id.addfood_edit_count);
        editCount.setParams(1d, 0.1d, 99999d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok), textWatcher);

        floatDecrementButton = view.findViewById(R.id.decrement_button);
        floatDecrementButton.setOnClickListener(this);

        floatIncrementButton = view.findViewById(R.id.increment_button);
        floatIncrementButton.setOnClickListener(this);

        if (isLastMeal == true) {
            lastMealText = view.findViewById(R.id.last_meal_text);
            lastMealText.setVisibility(View.VISIBLE);
            lastMealText.setText(food.name + ", " + food.portion + " " + food.units);
        }

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
            case R.id.ok:
                submit();
                break;
            case R.id.cancel:
                dismiss();
                break;
            case R.id.decrement_button:
                changePickerValue(-1);
                break;
            case R.id.increment_button:
                changePickerValue(+1);
                break;
        }
    }

    private void changePickerValue(double changeValue) {
        double oldValue = editCount.getValue();
        double newValue = oldValue + changeValue;
        editCount.setValue(newValue);
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            addFood(editCount);
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void addFood(NumberPicker editCount) {
        double count = editCount.getValue().doubleValue();
        if (count > 0) {
            Food foodCopy = FoodService.cloneFood(food);
            multiplyCountByPortions(foodCopy, count);
            if (isLastMeal == true) {
                FoodService.clearFoodCountAdded();
            } else {
                FoodService.setLastFood(foodCopy);
                addFoodNow(foodCopy);
            }
            if (isLastMeal == true && FoodService.getLastFood() != null) {
                EcarbBolusService.generateTreatmentWithSummary(getContext(), getFragmentManager(), Collections.singletonList(foodCopy));
            }
        }
    }

    private void addFoodNow(Food food) {
        FoodService.addFoodToList(food);
        FoodService.updateFoodCountAdded();
    }

    private Food multiplyCountByPortions(Food food, double count) {
        food.portionCount *= count;
        return food;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
