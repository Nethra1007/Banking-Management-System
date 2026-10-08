@echo off
REM Installs Python packages (first run) and starts Streamlit on http://localhost:8501
cd /d "%~dp0"
python -m pip install -r requirements.txt
python -m streamlit run streamlit_app.py
