"""Minimal SMTP sink for local development: accepts and discards every message.
Stands in for MailHog when Docker is unavailable."""
import asyncio

async def handle(reader, writer):
    async def send(line): writer.write(line.encode() + b"\r\n"); await writer.drain()
    await send("220 localhost SMTP sink")
    while True:
        line = await reader.readline()
        if not line: break
        cmd = line.decode(errors="replace").strip().upper()
        if cmd.startswith("EHLO") or cmd.startswith("HELO"):
            await send("250-localhost"); await send("250 OK")
        elif cmd.startswith("DATA"):
            await send("354 End data with <CR><LF>.<CR><LF>")
            while True:
                d = await reader.readline()
                if not d or d.strip() == b".": break
            await send("250 OK: queued")
        elif cmd.startswith("QUIT"):
            await send("221 Bye"); break
        else:
            await send("250 OK")
    writer.close()

async def main():
    server = await asyncio.start_server(handle, "127.0.0.1", 1025)
    async with server: await server.serve_forever()

asyncio.run(main())
