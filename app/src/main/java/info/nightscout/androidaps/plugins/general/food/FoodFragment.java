package info.nightscout.androidaps.plugins.general.food;

import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.dialogs.AddFoodDialog;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SpinnerHelper;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

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
    TextView clearList;
    TextView passBolus;

    public static TextView foodCountAdded;

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
        foodCountAdded.setText(String.valueOf(FoodService.getFoodList().size()));
        clearList = view.findViewById(R.id.clear_list);
        clearList.setPaintFlags(clearList.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        clearList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.clear_list:
                        if (FoodService.getFoodList().size() > 0) {
                            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                            builder.setTitle("Potwierdzenie");
                            builder.setMessage("Czy chcesz opróżnić listę?");
                            builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                                synchronized (builder) {
                                    FoodService.clearFoodCountAdded();
                                }
                            });
                            builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                            builder.show();
                        }
                        break;
                }
            }
        });
        passBolus = view.findViewById(R.id.pass_bolus);
        passBolus.setPaintFlags(passBolus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        passBolus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.pass_bolus:
                        EcarbBolusService.generateTreatmentWithSummary(getContext(), getFragmentManager(), FoodService.getFoodList());
                        break;
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

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(FoodPlugin.getPlugin().getService().getFoodData());
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
        recyclerView.swapAdapter(new FoodFragment.RecyclerViewAdapter(filtered), true);
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.FoodsViewHolder> {

        List<Food> foodList;

        RecyclerViewAdapter(List<Food> foodList) {
            this.foodList = foodList;
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
            holder.energy.setText(MainApp.gs(R.string.shortenergy) + ": " + food.energy + "kcal");
            if (food.energy == 0)
                holder.energy.setVisibility(View.INVISIBLE);
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
                addBolus = (TextView) itemView.findViewById(R.id.food_add);
                addBolus.setOnClickListener(this);
                addBolus.setPaintFlags(addBolus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final Food food = (Food) v.getTag();
                switch (v.getId()) {
                    case R.id.food_add:
                        this.showAddFood(food);
                        break;
                }
            }

            private void showAddFood(Food food) {
                FragmentManager manager = getFragmentManager();
                new AddFoodDialog(food, false).show(manager, "AddFoodDialog");
            }

        }
    }

}
