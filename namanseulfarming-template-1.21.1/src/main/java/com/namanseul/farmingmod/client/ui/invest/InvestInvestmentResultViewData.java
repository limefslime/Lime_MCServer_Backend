package com.namanseul.farmingmod.client.ui.invest;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public record InvestInvestmentResultViewData(
        String projectId,
        @Nullable Integer investedAmount,
        @Nullable Integer projectTotal,
        @Nullable JsonObject progress,
        @Nullable JsonObject completion
) {}
