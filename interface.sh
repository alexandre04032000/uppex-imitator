#!/bin/bash

OS="$(uname)"

if [[ "$OS" == "Darwin" ]]; then
    echo "Operating System is macOS?"
    echo "Confirm(y/n)?"
    read resposta
    if [[ "$resposta" == "y" || "$resposta" == "Y" ]]; then
        echo "Proceed Then"
        mkdir -p Uppex_Interface
        cd Uppex_Interface || exit
        open -a Docker
        docker pull imitator/imitator
        mkdir -p examples
        output_mac = $(docker run --rm --entrypoint /usr/bin/bash -v "$(pwd)":/examples imitator/imitator -c 'ls')
	if echo "$output_mac" | grep -q "^imitator$"; then
            echo "Imitator File Found!"
        else
	    echo "Imitator File Not Found ⚠️"
            echo "local image instalation"
            #docker pull imitator/imitator
            mkdir -p local_imitator
            cd local_imitator
            rm -rf .
            git clone https://github.com/imitator-model-checker/imitator.git "$pwd"
            docker build -rm imitator/imitator .
            cd ..
        fi
        # Downloads
        curl -L -o uppex.jar https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/uppex.jar
        curl -L -o Main.py https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/Main.py

        curl -L -o Tests.zip https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/Tests.zip \
          && unzip Tests.zip -d . \
          && rm Tests.zip

        curl -L -o static.zip https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/static.zip \
          && unzip static.zip -d . \
          && rm static.zip

        echo "Files Curled"

        python Main.py
            
    else
        echo "Shutting Down script..."
        exit 1
    fi 

else
    echo "Operating System is Windows?"
    echo "Confirm(y/n)?"
    read resposta
    if [[ "$resposta" == "y" || "$resposta" == "Y" ]]; then
        echo "Proceed Then"
        mkdir -p Uppex_Interface
        cd Uppex_Interface || exit
        powershell.exe Start-Process '"C:\Program Files\Docker\Docker\Docker Desktop.exe"'
        mkdir -p examples
        HOST_PATH="$(pwd)/../examples"
        curl -L -o fi.bat https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/fi.bat
        output=$(cmd.exe "/C fi.bat")
        if echo "$output" | grep -q "^imitator$"; then
            echo "Imitator File Found!"
        else
            echo "Imitator File Not Found ⚠️"
            echo "local image instalation"
            mkdir -p local_imitator
            cd local_imitator
            rm -rf .
            #docker pull imitator/imitator
	    #garantir a imagem local
            HOST_PATH2="$(pwd)/local_imitator"
            git clone https://github.com/imitator-model-checker/imitator.git "$HOST_PATH2"
            docker build -rm imitator/imitator .
            cd ..
        fi  

        # Downloads
        curl -L -o uppex.jar https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/uppex.jar
        curl -L -o Main.py https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/Main.py

        curl -L -o Tests.zip https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/Tests.zip \
          && unzip Tests.zip -d . \
          && rm Tests.zip

        curl -L -o static.zip https://github.com/alexandre04032000/uppex-imitator/releases/download/v1.1.0/static.zip \
          && unzip static.zip -d . \
          && rm static.zip

        echo "Files Curled"

        python Main.py
    else
        echo "Shutting Down script..."
        exit 1
    fi  
fi 
