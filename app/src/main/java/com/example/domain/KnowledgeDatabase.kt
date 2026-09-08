package com.example.domain

data class StandardItem(
    val code: String, // e.g. "IFRS 16"
    val title: String,
    val category: String, // "IFRS", "IAS", "UAE Tax Law", "ERP Guide"
    val summary: String,
    val applicableParagraphs: String,
    val recognitionRule: String,
    val measurementRule: String,
    val disclosureRule: String,
    val practicalExample: String,
    val officialSource: String
)

data class ErpGuideItem(
    val erpName: String, // "Zoho Books", "QuickBooks", "Xero", "Odoo", "SAP"
    val module: String, // "VAT Setup", "Corporate Tax", "WPS Payroll", "Chart of Accounts"
    val overview: String,
    val stepByStepConfig: List<String>,
    val bestPractices: List<String>
)

object KnowledgeDatabase {

    val standardsList = listOf(
        StandardItem(
            code = "UAE-CT-LAW-47",
            title = "Federal Decree-Law No. 47 of 2022 on Corporate Tax",
            category = "UAE Tax Law",
            summary = "Imposes a 9% corporate tax on taxable income exceeding AED 375,000 for mainland & non-qualifying free zone businesses.",
            applicableParagraphs = "Articles 3, 18, 20, 21, 51",
            recognitionRule = "Applies to resident companies, foreign entities managed in UAE, and natural persons with turnover > AED 1M.",
            measurementRule = "Taxable Income derived from accounting net profit under IFRS with statutory tax adjustments.",
            disclosureRule = "Mandatory tax return submission within 9 months of tax year-end on EmaraTax portal.",
            practicalExample = "AED 1M taxable net profit -> AED 375k @ 0% + AED 625k @ 9% = AED 56,250 Corporate Tax.",
            officialSource = "Federal Tax Authority (FTA) & Ministry of Finance"
        ),
        StandardItem(
            code = "UAE-VAT-LAW-8",
            title = "Federal Decree-Law No. 8 of 2017 on Value Added Tax",
            category = "UAE Tax Law",
            summary = "Imposes a standard 5% VAT on taxable supplies of goods and services in the UAE.",
            applicableParagraphs = "Articles 19, 25, 31, 45, 54",
            recognitionRule = "Mandatory registration if taxable supplies exceed AED 375,000 in past 12 months.",
            measurementRule = "5% calculated on price of supply. Tax credit claimed for input VAT paid on business expenses.",
            disclosureRule = "Quarterly or monthly tax return filing (Form VAT201) via EmaraTax.",
            practicalExample = "AED 100,000 invoice + 5% Output VAT (AED 5,000) - Input VAT Paid (AED 2,000) = Net Payable AED 3,000.",
            officialSource = "Federal Tax Authority (FTA)"
        ),
        StandardItem(
            code = "IFRS 16",
            title = "IFRS 16 Leases",
            category = "IFRS",
            summary = "Eliminates operating lease distinction for lessees. Recognizes Right-of-Use (ROU) Asset and Lease Liability.",
            applicableParagraphs = "Paragraphs 22–46, 51–60",
            recognitionRule = "Recognize ROU asset and lease liability at lease commencement date.",
            measurementRule = "Liability measured at PV of lease payments using Incremental Borrowing Rate (IBR). Asset depreciated straight-line.",
            disclosureRule = "Disclose depreciation of ROU assets, interest on lease liabilities, and cash outflows for leases.",
            practicalExample = "3-year lease @ AED 100k/yr -> PV ~ AED 285.9k. Recognize ROU Asset & Liability, depreciate AED 95.3k/yr.",
            officialSource = "IFRS Foundation / IASB"
        ),
        StandardItem(
            code = "IFRS 15",
            title = "IFRS 15 Revenue from Contracts with Customers",
            category = "IFRS",
            summary = "Establishes a 5-step framework to determine amount and timing of revenue recognition.",
            applicableParagraphs = "Paragraphs 9, 22, 46, 73, 105",
            recognitionRule = "Recognize revenue when control of promised goods/services transfers to customer.",
            measurementRule = "Transaction price allocated to performance obligations based on standalone selling prices.",
            disclosureRule = "Disclose disaggregated revenue, contract balances, and performance obligations.",
            practicalExample = "Software license + 1 year support bundle -> allocate transaction price based on standalone value of each component.",
            officialSource = "IFRS Foundation / IASB"
        ),
        StandardItem(
            code = "IFRS 9",
            title = "IFRS 9 Financial Instruments",
            category = "IFRS",
            summary = "Covers classification, measurement, impairment (Expected Credit Loss - ECL), and hedge accounting.",
            applicableParagraphs = "Paragraphs 4.1.1, 5.5.1, 5.5.15",
            recognitionRule = "Recognize financial assets at fair value or amortized cost based on business model and SPPI test.",
            measurementRule = "Impairment calculated using 3-stage Expected Credit Loss (ECL) model for trade receivables.",
            disclosureRule = "Disclose credit risk exposure, loss allowances, and interest income.",
            practicalExample = "Calculate 12-month or lifetime ECL provision matrix on trade receivables aging buckets.",
            officialSource = "IFRS Foundation / IASB"
        ),
        StandardItem(
            code = "IAS 16",
            title = "IAS 16 Property, Plant and Equipment",
            category = "IAS",
            summary = "Accounting treatment for property, plant, and equipment including cost recognition, depreciation, and revaluation.",
            applicableParagraphs = "Paragraphs 7, 30, 43, 50, 73",
            recognitionRule = "Capitalize if future economic benefits are probable and cost can be measured reliably.",
            measurementRule = "Initial cost includes purchase price + directly attributable costs. Subsequent measurement using Cost or Revaluation model.",
            disclosureRule = "Disclose measurement bases, depreciation methods, gross carrying amount, and accumulated depreciation.",
            practicalExample = "Factory machine purchased for AED 500k + installation AED 50k = initial cost AED 550k.",
            officialSource = "IFRS Foundation / IASB"
        ),
        StandardItem(
            code = "IAS 2",
            title = "IAS 2 Inventories",
            category = "IAS",
            summary = "Prescribes accounting treatment for inventory valuation at lower of cost and net realizable value (NRV).",
            applicableParagraphs = "Paragraphs 9, 10, 28, 36",
            recognitionRule = "Assets held for sale in ordinary course of business or materials consumed in production.",
            measurementRule = "Lower of Cost (FIFO or Weighted Average) and Net Realizable Value (Estimated selling price less completion costs).",
            disclosureRule = "Disclose total carrying amount, inventory expenses in COGS, and write-down amounts.",
            practicalExample = "Cost = AED 100/unit. NRV drops to AED 80/unit due to obsolescence -> write down AED 20/unit to P&L.",
            officialSource = "IFRS Foundation / IASB"
        ),
        StandardItem(
            code = "UAE-LABOUR-33",
            title = "UAE Federal Decree-Law No. 33 of 2021 (Labour Law)",
            category = "UAE Compliance",
            summary = "Regulates employment contracts, Wage Protection System (WPS) payroll, and End of Service Gratuity (EOSG).",
            applicableParagraphs = "Articles 16, 51, 52",
            recognitionRule = "Mandatory enrollment in WPS and statutory gratuity provision for full-time employees after 1 year.",
            measurementRule = "21 days basic salary/yr for first 5 years; 30 days basic salary/yr beyond 5 years. Capped at 2 yrs salary.",
            disclosureRule = "Monthly WPS SIF file upload to Central Bank and Ministry of Human Resources & Emiratisation (MOHRE).",
            practicalExample = "5 years service @ AED 10,000 basic -> 5 × 21 × (120,000/365) = AED 34,520 EOSG payable.",
            officialSource = "MOHRE & UAE Central Bank"
        )
    )

    val erpGuides = listOf(
        ErpGuideItem(
            erpName = "Zoho Books",
            module = "UAE VAT & Corporate Tax Setup",
            overview = "Configure UAE Tax Settings, TRN number, Tax Rates (5%, 0%, Exempt), and generate automated FTA VAT 201 return reports.",
            stepByStepConfig = listOf(
                "Navigate to Settings -> Taxes -> Tax Settings.",
                "Select 'United Arab Emirates' as Country and enter your 15-digit TRN.",
                "Enable 'UAE Corporate Tax' tracking and set fiscal year start date (e.g., 01 Jan).",
                "Ensure standard tax rates (5% VAT, 0% Export, Exempt) are enabled in Tax Rates master.",
                "Map Output VAT account to 'Tax Payable' and Input VAT to 'Tax Recoverable' in Chart of Accounts.",
                "Run the 'VAT Return (VAT201)' report under Reports -> Taxes to export XML for EmaraTax submission."
            ),
            bestPractices = listOf(
                "Tag all vendor bills with valid TRN numbers for input tax credit verification.",
                "Attach digital PDF copies of invoices to all transaction vouchers for FTA audit trail.",
                "Use Zoho Inventory integration to ensure item-level tax codes map automatically on sales orders."
            )
        ),
        ErpGuideItem(
            erpName = "Zoho Payroll",
            module = "UAE WPS Payroll & EOSG Automation",
            overview = "Automate monthly Salary Information File (SIF) generation for UAE WPS compliance and automated Gratuity accruals.",
            stepByStepConfig = listOf(
                "Go to Settings -> Company Profile -> Enable UAE Jurisdiction.",
                "Enter MOHRE Employer ID (13 digits) and Bank SIF Routing Code.",
                "Configure Pay Components: Basic Salary, Housing Allowance, Transport Allowance.",
                "Set End of Service Gratuity calculation rules matching Federal Decree-Law No. 33 of 2021.",
                "Generate monthly pay runs and download .SIF file for bank processing."
            ),
            bestPractices = listOf(
                "Process payroll before the 28th of every month to avoid MOHRE WPS blockage penalties.",
                "Reconcile WPS SIF payout summary against bank debit advice monthly."
            )
        ),
        ErpGuideItem(
            erpName = "QuickBooks Online",
            module = "UAE Tax & Multi-Currency Setup",
            overview = "Configure custom VAT 5% agency, multi-currency AED/USD transactions, and chart of accounts mapping.",
            stepByStepConfig = listOf(
                "Go to Taxes -> Tax Center -> Add Custom Tax Agency 'Federal Tax Authority'.",
                "Set standard rate 5.0% for Sales & Purchases.",
                "Configure Chart of Accounts: Add 'VAT Payable' (Liability) and 'VAT Input Credit' (Asset).",
                "Enable Multi-Currency under Account Settings for AED base currency conversion."
            ),
            bestPractices = listOf(
                "Review 'VAT Detail Report' prior to quarterly tax return filing.",
                "Ensure foreign invoice currency exchange rates match UAE Central Bank published daily rates."
            )
        ),
        ErpGuideItem(
            erpName = "Xero Accounting",
            module = "UAE Tax Rates & Financial Statements",
            overview = "Set up Xero Tax Rates, bank feeds, and custom IFRS compliant financial statement layouts.",
            stepByStepConfig = listOf(
                "Go to Advanced -> Tax Rates -> Add 'UAE VAT 5%' rate.",
                "Assign Tax Rate to revenue and expense account categories.",
                "Use Xero Analytics to track Working Capital, Current Ratio, and IFRS lease liability schedules."
            ),
            bestPractices = listOf(
                "Reconcile bank accounts daily using automated bank feeds.",
                "Export monthly Trial Balance in CSV format for auditor review."
            )
        ),
        ErpGuideItem(
            erpName = "Odoo ERP",
            module = "UAE Accounting Localization",
            overview = "Install `l10n_ae` UAE Localization module for automated VAT 201 reports, QR codes on invoices, and Corporate Tax schedules.",
            stepByStepConfig = listOf(
                "Go to Apps -> Install 'United Arab Emirates - Accounting'.",
                "Set Company TRN in Accounting -> Settings.",
                "Ensure Customer / Vendor Tax ID field is populated.",
                "Generate FTA VAT 201 report directly from Accounting -> Reporting -> UAE VAT Return."
            ),
            bestPractices = listOf(
                "Enable QR Code generation on customer invoices for UAE e-Invoicing compliance.",
                "Perform inventory valuation matching IAS 2 FIFO / Average Costing in Odoo Stock module."
            )
        )
    )
}
