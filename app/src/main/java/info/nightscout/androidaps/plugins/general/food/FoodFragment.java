package info.nightscout.androidaps.plugins.general.food;

import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Joiner;

import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.QuickWizardEntry;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.dialogs.AddFoodDialog;
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.SpinnerHelper;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static info.nightscout.androidaps.utils.DateUtil.now;

/**
 * Created by mike on 16.10.2017.
 */

public class FoodFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(FoodFragment.class);
    private CompositeDisposable disposable = new CompositeDisposable();

    EditText filter;
    ImageView clearFilter;
    SpinnerHelper category;
    SpinnerHelper subcategory;
    RecyclerView recyclerView;

    List<Food> unfiltered;
    List<Food> filtered;
    ArrayList<CharSequence> categories;
    ArrayList<CharSequence> subcategories;
    TextView foodCountAdded;
    TextView editAddedFood;
    TextView passBolus;

    final String EMPTY = MainApp.gs(R.string.none);

    private boolean accepted;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.food_fragment, container, false);
        filter = (EditText) view.findViewById(R.id.food_filter);
        clearFilter = (ImageView) view.findViewById(R.id.food_clearfilter);
        category = new SpinnerHelper(view.findViewById(R.id.food_category));
        subcategory = new SpinnerHelper(view.findViewById(R.id.food_subcategory));
        foodCountAdded = view.findViewById(R.id.food_count_added);
        foodCountAdded.setText(String.valueOf(FoodService.getFoodListSize()));
        editAddedFood = view.findViewById(R.id.edit_added_food);
        editAddedFood.setPaintFlags(editAddedFood.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        passBolus = view.findViewById(R.id.pass_bolus);
        passBolus.setPaintFlags(passBolus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        passBolus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.pass_bolus:
                        List<Food> foodList = FoodService.getFoodList();
                        int wbt = this.calculateWBT(foodList);
                        int carbs = getCarbsSum(foodList);

                        if (wbt > 0) {
                            this.addEcarbs(wbt);
                        }
                        if (carbs > 0) {
                            this.addBolus(carbs);
                        }
                        break;

                    case R.id.edit_added_food:
                        break;
                }
            }


            private int calculateWBT(List<Food> foodList) {
                int wbt = 0;
                for (Food food : foodList) {
                    wbt += calculateWBT(food);
                }
                return wbt;
            }

            private int calculateWBT(Food food) {
                final int kcalPerOneCarb = 4;
                final int kcalPerOneFat = 9;
                final int kcalPerOneProtein = 4;

                Double wbt;
                if (food.energy > 0) {
                    wbt = SafeParse.stringToDouble(
                            String.valueOf(
                                    (food.energy - kcalPerOneCarb * food.carbs) / 100
                            )
                    );
                } else {
                    wbt = SafeParse.stringToDouble(
                            String.valueOf(
                                    (food.fat * kcalPerOneFat + food.protein * kcalPerOneProtein) / 100
                            )
                    );
                }

                return (int) Math.floor(wbt);
            }

            private void addEcarbs(int wbt) {
                List<String> actions = new LinkedList<>();

                int eCarbs = wbt * 10;
                Integer duration;
                if (wbt > 4) {
                    duration = 8;
                } else {
                    duration = wbt + 2;
                }
                Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(eCarbs)).value();

                int timeOffset = 0;
                final long time = now() + timeOffset * 1000 * 60;
                if (timeOffset != 0) {
                    actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(time));
                }

                if (duration > 0) {
                    actions.add(MainApp.gs(R.string.duration) + ": " + duration + MainApp.gs(R.string.shorthour));
                }

                if (eCarbs > 0) {
                    actions.add("Węglow. złożone" + ": " + "<font color='" + MainApp.gc(R.color.carbs) + "'>" + carbsAfterConstraints + "g" + "</font>");
                }
                if (!carbsAfterConstraints.equals(eCarbs)) {
                    actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.carbsconstraintapplied) + "</font>");
                }

                if (carbsAfterConstraints > 0) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Potwierdzenie eCarb");
                    builder.setMessage(Html.fromHtml(Joiner.on("<br/>").join(actions)));
                    builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                        synchronized (builder) {
                            if (accepted) {
                                log.debug("guarding: already accepted");
                                return;
                            }
                            accepted = true;

                            if (carbsAfterConstraints > 0) {
                                if (duration == 0) {
                                    CarbsGenerator.createCarb(carbsAfterConstraints, time, CareportalEvent.CARBCORRECTION, "");
                                } else {
                                    CarbsGenerator.generateCarbs(carbsAfterConstraints, time, duration, "");
                                    NSUpload.uploadEvent(CareportalEvent.NOTE, now() - 2000, MainApp.gs(R.string.generated_ecarbs_note, carbsAfterConstraints, duration, timeOffset));
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                    builder.show();
                }
            }

            private int getCarbsSum(List<Food> foodList) {
                int carbs = 0;
                for (Food food : foodList) {
                    carbs += food.carbs;
                }
                return carbs;
            }

            private void addBolus(int carbs) {
                try {
                    BolusWizard wizard = onClickQuickwizard(carbs);
                    wizard.confirmAndExecute(getContext());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            public BolusWizard onClickQuickwizard(Integer carbs) throws JSONException {
//                final BgReading actualBg = DatabaseHelper.actualBg();
                final BgReading actualBg = new BgReading();
                actualBg.value = 120;
                actualBg.date = 1573079975L;
                actualBg.raw = 0;
                final Profile profile = ProfileFunctions.getInstance().getProfile();
                final String profileName = ProfileFunctions.getInstance().getProfileName();
                final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

                JSONObject input = new JSONObject("{\"buttonText\":\"\",\"carbs\":" + carbs + ",\"validFrom\":0,\"validTo\":86340, \"useBG\":1, \"useBolusIOB\":1, \"useBasalIOB\":1}");
                final QuickWizardEntry quickWizardEntry = new QuickWizardEntry(input, -1);
                if (quickWizardEntry != null && actualBg != null && profile != null && pump != null) {
                    final BolusWizard wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true);

                    if (wizard.getCalculatedTotalInsulin() > 0d && quickWizardEntry.carbs() > 0d) {
                        Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(quickWizardEntry.carbs())).value();

                        if (Math.abs(wizard.getInsulinAfterConstraints() - wizard.getCalculatedTotalInsulin()) >= pump.getPumpDescription().pumpType.determineCorrectBolusStepSize(wizard.getInsulinAfterConstraints()) || !carbsAfterConstraints.equals(quickWizardEntry.carbs())) {
                            OKDialog.show(getContext(), MainApp.gs(R.string.treatmentdeliveryerror), MainApp.gs(R.string.constraints_violation) + "\n" + MainApp.gs(R.string.changeyourinput), null);
                            return wizard;
                        }

                        return wizard;
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        });
        recyclerView = (RecyclerView) view.findViewById(R.id.food_recyclerview);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        clearFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filter.setText("");
                category.setSelection(0);
                subcategory.setSelection(0);
                filterData();
            }
        });

        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fillSubcategories();
                filterData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                fillSubcategories();
                filterData();
            }
        });

        subcategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                filterData();
            }
        });

        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterData();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(FoodPlugin.getPlugin().getService().getFoodData(), foodCountAdded);
        recyclerView.setAdapter(adapter);

        loadData();
        fillCategories();
        fillSubcategories();
        filterData();
        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventFoodDatabaseChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    void loadData() {
        unfiltered = FoodPlugin.getPlugin().getService().getFoodData();
    }

    void fillCategories() {
        Set<CharSequence> catSet = new HashSet<>();

        for (Food f : unfiltered) {
            if (f.category != null && !f.category.equals(""))
                catSet.add(f.category);
        }

        // make it unique
        categories = new ArrayList<>(catSet);
        categories.add(0, MainApp.gs(R.string.none));

        ArrayAdapter<CharSequence> adapterCategories = new ArrayAdapter<>(getContext(),
                R.layout.spinner_centered, categories);
        category.setAdapter(adapterCategories);
    }

    void fillSubcategories() {
        String categoryFilter = category.getSelectedItem().toString();

        Set<CharSequence> subCatSet = new HashSet<>();

        if (!categoryFilter.equals(EMPTY)) {
            for (Food f : unfiltered) {
                if (f.category != null && f.category.equals(categoryFilter))
                    if (f.subcategory != null && !f.subcategory.equals(""))
                        subCatSet.add(f.subcategory);
            }
        }

        // make it unique
        subcategories = new ArrayList<>(subCatSet);
        subcategories.add(0, MainApp.gs(R.string.none));

        ArrayAdapter<CharSequence> adapterSubcategories = new ArrayAdapter<>(getContext(),
                R.layout.spinner_centered, subcategories);
        subcategory.setAdapter(adapterSubcategories);
    }

    void filterData() {
        String textFilter = filter.getText().toString();
        String categoryFilter = category.getSelectedItem().toString();
        String subcategoryFilter = subcategory.getSelectedItem().toString();

        filtered = new ArrayList<>();

        for (Food f : unfiltered) {
            if (f.name == null || f.category == null || f.subcategory == null)
                continue;

            if (!subcategoryFilter.equals(EMPTY) && !f.subcategory.equals(subcategoryFilter))
                continue;
            if (!categoryFilter.equals(EMPTY) && !f.category.equals(categoryFilter))
                continue;
            if (!textFilter.equals("") && !f.name.toLowerCase().contains(textFilter.toLowerCase()))
                continue;
            filtered.add(f);
        }

        updateGui();
    }

    protected void updateGui() {
        recyclerView.swapAdapter(new FoodFragment.RecyclerViewAdapter(filtered, foodCountAdded), true);
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.FoodsViewHolder> {

        List<Food> foodList;
        TextView foodCountAdded;

        RecyclerViewAdapter(List<Food> foodList, TextView foodCountAdded) {
            this.foodList = foodList;
            this.foodCountAdded = foodCountAdded;
        }

        @Override
        public FoodsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.food_item, viewGroup, false);
            return new FoodsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(FoodsViewHolder holder, int position) {
            Food food = foodList.get(position);
            holder.ns.setVisibility(food._id != null ? View.VISIBLE : View.GONE);
            holder.name.setText(food.name);
            holder.portion.setText(Double.valueOf(food.portion).intValue() + " " + food.units);
            holder.carbs.setText(food.carbs + MainApp.gs(R.string.shortgramm));
            holder.fat.setText(MainApp.gs(R.string.shortfat) + ": " + food.fat + MainApp.gs(R.string.shortgramm));
            if (food.fat == 0)
                holder.fat.setVisibility(View.INVISIBLE);
            holder.protein.setText(MainApp.gs(R.string.shortprotein) + ": " + food.protein + MainApp.gs(R.string.shortgramm));
            if (food.protein == 0)
                holder.protein.setVisibility(View.INVISIBLE);
            holder.energy.setText(MainApp.gs(R.string.shortenergy) + ": " + food.energy + MainApp.gs(R.string.shortkilojoul));
            if (food.energy == 0)
                holder.energy.setVisibility(View.INVISIBLE);
            holder.remove.setTag(food);
            holder.addBolus.setTag(food);
        }

        @Override
        public int getItemCount() {
            return foodList.size();
        }

        class FoodsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView name;
            TextView portion;
            TextView carbs;
            TextView fat;
            TextView protein;
            TextView energy;
            TextView ns;
            TextView remove;
            TextView addBolus;

            FoodsViewHolder(View itemView) {
                super(itemView);
                name = (TextView) itemView.findViewById(R.id.food_name);
                portion = (TextView) itemView.findViewById(R.id.food_portion);
                carbs = (TextView) itemView.findViewById(R.id.food_carbs);
                fat = (TextView) itemView.findViewById(R.id.food_fat);
                protein = (TextView) itemView.findViewById(R.id.food_protein);
                energy = (TextView) itemView.findViewById(R.id.food_energy);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                remove = (TextView) itemView.findViewById(R.id.food_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                addBolus = (TextView) itemView.findViewById(R.id.food_add);
                addBolus.setOnClickListener(this);
                addBolus.setPaintFlags(addBolus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final Food food = (Food) v.getTag();
                switch (v.getId()) {
                    case R.id.food_remove:
                        this.showRemoveDialog(food);
                        break;

                    case R.id.food_add:
                        this.showAddFood(food, foodCountAdded);
                        break;
                }
            }

            private void showAddFood(Food food, TextView foodCountAdded) {
                FragmentManager manager = getFragmentManager();
                new AddFoodDialog(food, foodCountAdded).show(manager, "AddFoodDialog");
            }

            public void showRemoveDialog(Food food) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(MainApp.gs(R.string.confirmation));
                builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + food.name);
                builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        final String _id = food._id;
                        if (_id != null && !_id.equals("")) {
                            NSUpload.removeFoodFromNS(_id);
                        }
                        FoodPlugin.getPlugin().getService().delete(food);
                    }
                });
                builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                builder.show();
            }

        }
    }

}
