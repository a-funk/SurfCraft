# Railway serves the dev log as a static site (tools/devlog.py deploy).
FROM caddy:2-alpine
COPY . /srv
RUN rm -f /srv/Dockerfile /srv/.gitignore /srv/.lock
CMD ["sh", "-c", "exec caddy file-server --root /srv --listen :${PORT:-8080}"]
