#!/bin/sh
# Installs Python packages (first run) and starts Streamlit on http://localhost:8501
cd "$(dirname "$0")" || exit 1
python3 -m pip install -r requirements.txt
python3 -m streamlit run streamlit_app.py
