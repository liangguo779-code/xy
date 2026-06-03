from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from api.chat import router as chat_router
from api.knowledge import router as knowledge_router

app = FastAPI(title="校园 AI 中台", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router, tags=["chat"])
app.include_router(knowledge_router, tags=["knowledge"])


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
