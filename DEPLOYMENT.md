# Vercel Deployment

## Deployment Instructions

This project contains Java bytecode and source files. To deploy to Vercel:

### Option 1: Deploy via Vercel CLI
```bash
# Install Vercel CLI (if not already installed)
npm install -g vercel

# Deploy to Vercel
vercel --prod

# Or for a preview deployment
vercel
```

### Option 2: Deploy via Vercel Dashboard
1. Push this repository to your Vercel account
2. Select "Import from GitHub" 
3. Choose the repository `PoundSand89/Chase`
4. Configure the deployment settings

### Notes
- This deployment uses Vercel's Docker runtime
- Java applications on Vercel now run in containers via Docker
- For production Java applications, Docker is recommended for reliability

### Deployment Configuration
This project includes:
- Java bytecode (ChaseKeno$*.class files)
- Java source code (ChaseKeno.java)
- Vercel deployment configuration (package.json)
- Vercel Docker configuration (Dockerfile)
- Java application runner (Docker container)

### URLs
- GitHub Repository: https://github.com/PoundSand89/Chase.git
- Current Branch: main