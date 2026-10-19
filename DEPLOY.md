# Deploy guide: GitHub, Neon, Render, Gemini (sab free)

Is guide se aapka project ek public link par chalega. Websites ke buttons ke naam thode alag ho sakte hain, par steps wahi rahenge.

> Sirf nakli (sample) data use kijiye. Asli patient data mat daaliye.

## Step 1: Code GitHub par daaliye

1. github.com par naya repository banaiye.
2. Is poore folder ko usme upload kijiye (`.env` file kabhi upload mat kijiye. `.gitignore` use rok deta hai).

## Step 2: Neon database banaiye

1. neon.tech par sign up kijiye aur ek naya **Project** banaiye.
2. Dashboard par **Connect** dabaiye aur **connection string** copy kijiye. Wo `postgresql://...` se shuru hoti hai.
3. Isse sambhal kar rakhiye. Ye aapka `DATABASE_URL` hai. Tables server apne aap bana lega.

## Step 3: Gemini API key banaiye (AI ke liye)

1. aistudio.google.com par Google account se login kijiye.
2. **Get API key** se ek key banaiye aur copy kijiye. Ye `GEMINI_API_KEY` hai.
3. Free tier ki limits aapko AI Studio me dikhti hain. Model ka naam bhi wahin dekh sakte hain.

## Step 4: Render par deploy kijiye

1. render.com par GitHub se sign up kijiye.
2. **New** > **Web Service** chuniye aur apna repository connect kijiye.
3. **Language / Runtime: Docker** chuniye aur **Instance Type: Free**.
4. **Environment Variables** me ye daaliye:

| Name | Value |
|---|---|
| `DATABASE_URL` | Neon wali connection string |
| `ADMIN_USER` | jaise `admin` |
| `ADMIN_PASSWORD` | koi mazboot password (kam se kam 8 akshar) |
| `SESSION_SECRET` | koi lamba random text (16+ akshar) |
| `GEMINI_API_KEY` | Gemini wali key |
| `GEMINI_MODEL` | (optional) AI Studio me dikhne wala model, warna khali chhodiye |

5. **Create Web Service** dabaiye. Pehli build me kuch minute lagte hain.
6. Build poori hone par Render ek link dega, jaise `https://aapka-naam.onrender.com`. Wo aapka public link hai.

## Step 5: Use kijiye

- Link kholiye aur `ADMIN_USER` / `ADMIN_PASSWORD` se sign in kijiye.
- **Staff** page se receptionist, doctor ya nurse ke accounts banaiye.
- Free server 15 minute idle rehne par so jata hai, to pehli baar link kholne par lagbhag ek minute lag sakta hai. Kisi ko dikhane se pehle link khol kar rakhiye.

## Agar kuch na chale

| Dikkat | Kya dekhein |
|---|---|
| "Cannot start: DATABASE_URL is not set" | Render me variable sahi naam se daala hai ya nahi |
| Database connection error | Neon string poori copy hui hai (`?sslmode=require` samet) |
| AI "not set up" bata raha hai | `GEMINI_API_KEY` daali hai ya nahi |
| AI "could not answer" bata raha hai | Render ke **Logs** dekhiye. Model ka naam badal gaya ho to `GEMINI_MODEL` AI Studio ke hisaab se badaliye |
| Login par "Too many attempts" | 10 minute ruk kar dobara koshish kijiye |

## Local par chalana

```bash
cp .env.example .env     # .env me apni values bharein
mvn package
java -jar target/hospital-management.jar
```

Phir http://localhost:8080 kholiye.
