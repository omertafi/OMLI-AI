package com.example.domain

import java.text.DecimalFormat

object TaxCalculators {

    private val currencyFormat = DecimalFormat("#,##0.00")

    fun formatMoney(amount: Double): String = "AED ${currencyFormat.format(amount)}"

    // 1. UAE CORPORATE TAX CALCULATOR
    data class CorporateTaxResult(
        val taxableIncome: Double,
        val taxPayable: Double,
        val effectiveTaxRate: Double,
        val netProfitAfterTax: Double,
        val breakdownSteps: List<String>,
        val legalBasis: String
    )

    fun calculateCorporateTax(
        annualRevenue: Double,
        taxableIncome: Double,
        isFreeZone: Boolean,
        isSmallBusinessRelief: Boolean
    ): CorporateTaxResult {
        val steps = mutableListOf<String>()

        if (taxableIncome <= 0) {
            steps.add("Taxable income is non-positive (${formatMoney(taxableIncome)}). No corporate tax is due.")
            return CorporateTaxResult(
                taxableIncome = taxableIncome,
                taxPayable = 0.0,
                effectiveTaxRate = 0.0,
                netProfitAfterTax = taxableIncome,
                breakdownSteps = steps,
                legalBasis = "UAE Federal Decree-Law No. 47 of 2022, Article 3"
            )
        }

        if (isSmallBusinessRelief) {
            if (annualRevenue <= 3_000_000.0) {
                steps.add("Small Business Relief Elected (Revenue ${formatMoney(annualRevenue)} ≤ AED 3,000,000 threshold).")
                steps.add("Taxable Income treated as ZERO under Ministerial Decision No. 73 of 2023.")
                return CorporateTaxResult(
                    taxableIncome = taxableIncome,
                    taxPayable = 0.0,
                    effectiveTaxRate = 0.0,
                    netProfitAfterTax = taxableIncome,
                    breakdownSteps = steps,
                    legalBasis = "Ministerial Decision No. 73 of 2023 & Decree-Law No. 47 Article 21"
                )
            } else {
                steps.add("Small Business Relief Disqualified: Revenue ${formatMoney(annualRevenue)} exceeds AED 3,000,000 cap.")
            }
        }

        if (isFreeZone) {
            steps.add("Qualifying Free Zone Person status assumed for Qualifying Income (0% rate).")
            steps.add("Ensure de minimis rule (non-qualifying revenue ≤ 5% or AED 5M) is satisfied.")
            return CorporateTaxResult(
                taxableIncome = taxableIncome,
                taxPayable = 0.0,
                effectiveTaxRate = 0.0,
                netProfitAfterTax = taxableIncome,
                breakdownSteps = steps,
                legalBasis = "Cabinet Decision No. 100 of 2023 & Decree-Law No. 47 Article 18"
            )
        }

        val threshold = 375_000.0
        val taxRate = 0.09

        val tax: Double
        if (taxableIncome <= threshold) {
            steps.add("Taxable Income (${formatMoney(taxableIncome)}) is below the AED 375,000 threshold.")
            steps.add("Applied Rate: 0%")
            tax = 0.0
        } else {
            val taxableAboveThreshold = taxableIncome - threshold
            tax = taxableAboveThreshold * taxRate
            steps.add("First AED 375,000 @ 0% = AED 0.00")
            steps.add("Taxable Amount above AED 375,000 = ${formatMoney(taxableAboveThreshold)}")
            steps.add("Tax Calculated (${formatMoney(taxableAboveThreshold)} × 9%) = ${formatMoney(tax)}")
        }

        val effectiveRate = if (taxableIncome > 0) (tax / taxableIncome) * 100 else 0.0

        return CorporateTaxResult(
            taxableIncome = taxableIncome,
            taxPayable = tax,
            effectiveTaxRate = effectiveRate,
            netProfitAfterTax = taxableIncome - tax,
            breakdownSteps = steps,
            legalBasis = "UAE Federal Decree-Law No. 47 of 2022, Articles 3 & 20"
        )
    }

    // 2. UAE VAT CALCULATOR
    enum class VatType {
        EXCLUSIVE_5, INCLUSIVE_5, ZERO_RATED, EXEMPT
    }

    data class VatResult(
        val baseAmount: Double,
        val outputVat: Double,
        val totalInvoiceAmount: Double,
        val inputVatCredit: Double,
        val netPayableToFta: Double,
        val journalEntrySummary: String,
        val legalBasis: String
    )

    fun calculateVat(
        amount: Double,
        type: VatType,
        inputVatPaid: Double = 0.0
    ): VatResult {
        var base = amount
        var vat = 0.0
        var total = amount

        when (type) {
            VatType.EXCLUSIVE_5 -> {
                vat = amount * 0.05
                total = amount + vat
            }
            VatType.INCLUSIVE_5 -> {
                vat = amount * (5.0 / 105.0)
                base = amount - vat
                total = amount
            }
            VatType.ZERO_RATED -> {
                vat = 0.0
                total = amount
            }
            VatType.EXEMPT -> {
                vat = 0.0
                total = amount
            }
        }

        val netPayable = vat - inputVatPaid

        val journal = """
Dr. Accounts Receivable / Bank: ${formatMoney(total)}
  Cr. Revenue (Base Amount): ${formatMoney(base)}
  Cr. Output VAT Payable (5%): ${formatMoney(vat)}
        """.trimIndent()

        val basis = when (type) {
            VatType.EXCLUSIVE_5, VatType.INCLUSIVE_5 -> "UAE Federal Decree-Law No. 8 of 2017, Article 3 & 25 (Standard 5%)"
            VatType.ZERO_RATED -> "UAE Federal Decree-Law No. 8 of 2017, Article 45 (Zero-Rated Supplies)"
            VatType.EXEMPT -> "UAE Federal Decree-Law No. 8 of 2017, Article 46 (Exempt Supplies)"
        }

        return VatResult(
            baseAmount = base,
            outputVat = vat,
            totalInvoiceAmount = total,
            inputVatCredit = inputVatPaid,
            netPayableToFta = netPayable,
            journalEntrySummary = journal,
            legalBasis = basis
        )
    }

    // 3. UAE END OF SERVICE GRATUITY (EOSG) CALCULATOR
    data class GratuityResult(
        val totalYears: Double,
        val dailyBasicSalary: Double,
        val gratuityAmount: Double,
        val breakdownSteps: List<String>,
        val legalBasis: String
    )

    fun calculateGratuity(
        basicMonthlySalary: Double,
        totalMonthlySalary: Double,
        yearsOfService: Int,
        monthsOfService: Int
    ): GratuityResult {
        val totalYears = yearsOfService + (monthsOfService / 12.0)
        val dailyBasic = (basicMonthlySalary * 12.0) / 365.0
        val steps = mutableListOf<String>()

        steps.add("Basic Monthly Salary: ${formatMoney(basicMonthlySalary)}")
        steps.add("Daily Basic Salary Formula = (Basic × 12) / 365 = ${formatMoney(dailyBasic)}")
        steps.add("Total Duration of Service: $yearsOfService Years, $monthsOfService Months (${String.format("%.2f", totalYears)} total years)")

        if (totalYears < 1.0) {
            steps.add("Service duration is less than 1 full year. Under UAE Labour Law, no gratuity is payable.")
            return GratuityResult(
                totalYears = totalYears,
                dailyBasicSalary = dailyBasic,
                gratuityAmount = 0.0,
                breakdownSteps = steps,
                legalBasis = "UAE Federal Decree-Law No. 33 of 2021, Article 51"
            )
        }

        var amount = 0.0

        if (totalYears <= 5.0) {
            val days = totalYears * 21.0
            amount = days * dailyBasic
            steps.add("First 5 Years Rule: 21 days basic salary per year.")
            steps.add("Total Gratuity Days = ${String.format("%.2f", days)} days")
            steps.add("Gratuity Calculation = ${String.format("%.2f", days)} days × ${formatMoney(dailyBasic)} = ${formatMoney(amount)}")
        } else {
            val first5Days = 5.0 * 21.0
            val remainingYears = totalYears - 5.0
            val remainingDays = remainingYears * 30.0
            val totalDays = first5Days + remainingDays

            amount = totalDays * dailyBasic
            steps.add("First 5 Years: 5 yrs × 21 days = 105 days basic salary.")
            steps.add("Years Beyond 5 (${String.format("%.2f", remainingYears)} yrs): ${String.format("%.2f", remainingYears)} × 30 days = ${String.format("%.2f", remainingDays)} days.")
            steps.add("Total Accumulated Gratuity Days = ${String.format("%.2f", totalDays)} days")
            steps.add("Subtotal Amount = ${formatMoney(amount)}")
        }

        // Maximum Cap Check (2 Years Total Salary = 24 Months Total Salary)
        val maxCap = totalMonthlySalary * 24.0
        if (amount > maxCap) {
            steps.add("MAXIMUM CAP APPLIED: Statutory EOSG cannot exceed 2 years' total salary (${formatMoney(maxCap)}).")
            amount = maxCap
        }

        return GratuityResult(
            totalYears = totalYears,
            dailyBasicSalary = dailyBasic,
            gratuityAmount = amount,
            breakdownSteps = steps,
            legalBasis = "UAE Federal Decree-Law No. 33 of 2021 (Labour Relations), Article 51"
        )
    }

    // 4. FINANCIAL RATIO & WORKING CAPITAL ANALYZER
    data class FinancialRatiosResult(
        val currentRatio: Double,
        val quickRatio: Double,
        val debtToEquity: Double,
        val netProfitMarginPct: Double,
        val workingCapital: Double,
        val solvencyStatus: String,
        val liquidityStatus: String,
        val recommendations: List<String>
    )

    fun analyzeRatios(
        currentAssets: Double,
        currentLiabilities: Double,
        cashAndEquivalents: Double,
        inventory: Double,
        totalDebt: Double,
        totalEquity: Double,
        revenue: Double,
        netIncome: Double
    ): FinancialRatiosResult {
        val currRatio = if (currentLiabilities > 0) currentAssets / currentLiabilities else 0.0
        val qkRatio = if (currentLiabilities > 0) (currentAssets - inventory) / currentLiabilities else 0.0
        val dToE = if (totalEquity > 0) totalDebt / totalEquity else 0.0
        val marginPct = if (revenue > 0) (netIncome / revenue) * 100 else 0.0
        val workCap = currentAssets - currentLiabilities

        val liqStatus = when {
            currRatio >= 1.5 -> "Healthy Liquidity (Current Ratio: ${String.format("%.2f", currRatio)})"
            currRatio >= 1.0 -> "Adequate Liquidity (Current Ratio: ${String.format("%.2f", currRatio)})"
            else -> "Liquidity Warning (Current Ratio < 1.0) - High risk of short-term default"
        }

        val solvStatus = when {
            dToE <= 1.5 -> "Low Financial Leverage (D/E: ${String.format("%.2f", dToE)})"
            dToE <= 3.0 -> "Moderate Financial Leverage (D/E: ${String.format("%.2f", dToE)})"
            else -> "High Debt Risk (D/E > 3.0) - Potential solvency concern under bank covenants"
        }

        val recs = mutableListOf<String>()
        if (currRatio < 1.2) recs.add("Improve cash collection cycles (DSO) and restructure short-term liabilities.")
        if (inventory > (currentAssets * 0.4)) recs.add("High inventory holding detected. Perform NRV write-down test under IAS 2.")
        if (marginPct < 5.0) recs.add("Review direct cost of goods sold and overhead allocations to optimize profit margin.")
        recs.add("Ensure compliance with IAS 1 presentation of current vs non-current assets and liabilities.")

        return FinancialRatiosResult(
            currentRatio = currRatio,
            quickRatio = qkRatio,
            debtToEquity = dToE,
            netProfitMarginPct = marginPct,
            workingCapital = workCap,
            solvencyStatus = solvStatus,
            liquidityStatus = liqStatus,
            recommendations = recs
        )
    }
}
