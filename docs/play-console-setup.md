# Play Console Manual Setup Steps

## 1. Create Subscription Product (ptk_monthly)

1. Go to https://play.google.com/console/u/0/developers/8461539450232611708/app-list
2. Select PoliticalTextKiller (create the app first if not done — see step 3)
3. Navigate to **Monetize > Products > Subscriptions**
4. Click **Create subscription**
5. Fill in:
   - Product ID: `ptk_monthly`
   - Name: `PoliticalTextKiller Pro`
6. Click **Add a base plan**:
   - Base plan ID: `monthly`
   - Billing period: **1 month**
   - Price: **$0.99 USD** (set price for all regions)
   - Renewal type: **Auto-renewing**
7. Click **Add an offer** on the base plan:
   - Offer ID: `free-trial`
   - Eligibility: **New customer acquisition**
   - Add phase: **Free trial, 30 days**
8. Activate the base plan and offer
9. Save the subscription

## 2. Complete SMS/Call Log Declaration

1. In the app's Play Console page, go to **Policy and programs > App content**
2. Find **Permissions declaration** or **SMS/Call Log declaration**
3. Fill in:
   - **Core functionality**: "The app reads SMS messages to classify and delete political spam. This is the app's sole purpose."
   - **Default SMS handler**: "The app can optionally act as default SMS handler to enable batch deletion. Users are prompted to switch back immediately after."
   - **Alternatives**: "The app also supports Shizuku for silent deletion without being default SMS handler."
4. You may need to provide a video demo showing the SMS functionality
5. Submit the declaration

## 3. Create the App (if not already done)

1. Click **Create app** in Play Console
2. App name: `PoliticalTextKiller`
3. Default language: English (United States)
4. App type: App
5. Free or paid: Paid (subscription model, but the app download is free)
   - Actually select **Free** since it's free to download with in-app subscription
6. Declarations: Check all required boxes

## 4. Store Listing

1. Go to **Grow > Store presence > Main store listing**
2. Copy content from `docs/store-listing.md`
3. Upload screenshots (phone, 2-8 required):
   - Take screenshots from the running app on device
   - Minimum 320px, maximum 3840px per side
4. Upload feature graphic (1024x500 PNG/JPG)
5. Upload app icon (512x512 PNG — this should match the launcher icon)

## 5. Content Rating

1. Go to **Policy and programs > App content > Content rating**
2. Complete the IARC questionnaire
3. Key answers:
   - No violence/sexual content
   - No user-generated content
   - No data sharing
   - Age rating target: Everyone

## 6. Target Audience and Content

1. **Target audience**: 18+ (political content context)
2. **Not designed for children**: Confirm

## 7. Data Safety

1. Go to **Policy and programs > App content > Data safety**
2. Does your app collect or share data? **No** (all on-device)
3. Does your app handle SMS? **Yes — for core functionality (spam deletion)**
   - Data is processed on-device only
   - Data is not transmitted to servers
   - Data can be deleted by the user (vault/kill log clear)
