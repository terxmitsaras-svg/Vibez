# Upload Vibez to GitHub

I can’t directly upload this repository to your personal GitHub account from this environment because there is no GitHub CLI/auth configured here.

Use these commands on your machine to publish and download it:

```bash
cd /path/to/Vibez

git init
# If this repo already has commits, skip the next three lines
# git add .
# git commit -m "Initial commit"

git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

Then you can download it anytime with:

```bash
git clone https://github.com/<your-username>/<your-repo>.git
```

Or as a ZIP from the GitHub repository page: **Code → Download ZIP**.
