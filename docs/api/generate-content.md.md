# Generating content

The Gemini API supports content generation with images, audio, code, tools, and more. For details on each of these features, read on and check out the task-focused sample code, or read the comprehensive guides.

- [Text generation](https://ai.google.dev/gemini-api/docs/text-generation)
- [Vision](https://ai.google.dev/gemini-api/docs/vision)
- [Audio](https://ai.google.dev/gemini-api/docs/audio)
- [Embeddings](https://ai.google.dev/gemini-api/docs/embeddings)
- [Long context](https://ai.google.dev/gemini-api/docs/long-context)
- [Code execution](https://ai.google.dev/gemini-api/docs/code-execution)
- [JSON Mode](https://ai.google.dev/gemini-api/docs/json-mode)
- [Function calling](https://ai.google.dev/gemini-api/docs/function-calling)
- [System instructions](https://ai.google.dev/gemini-api/docs/system-instructions)

## Method: models.generateContent

- [Endpoint](https://ai.google.dev/api/generate-content#body.HTTP_TEMPLATE)
- [Path parameters](https://ai.google.dev/api/generate-content#body.PATH_PARAMETERS)
- [Request body](https://ai.google.dev/api/generate-content#body.request_body)
  - [JSON representation](https://ai.google.dev/api/generate-content#body.request_body.SCHEMA_REPRESENTATION)
- [Response body](https://ai.google.dev/api/generate-content#body.response_body)
- [Authorization scopes](https://ai.google.dev/api/generate-content#body.aspect)
- [Example request](https://ai.google.dev/api/generate-content#body.codeSnippets)
  - [Text](https://ai.google.dev/api/generate-content#body.codeSnippets.group)
  - [Image](https://ai.google.dev/api/generate-content#body.codeSnippets.group_1)
  - [Audio](https://ai.google.dev/api/generate-content#body.codeSnippets.group_2)
  - [Video](https://ai.google.dev/api/generate-content#body.codeSnippets.group_3)
  - [PDF](https://ai.google.dev/api/generate-content#body.codeSnippets.group_4)
  - [Chat](https://ai.google.dev/api/generate-content#body.codeSnippets.group_5)
  - [Cache](https://ai.google.dev/api/generate-content#body.codeSnippets.group_6)
  - [Tuned Model](https://ai.google.dev/api/generate-content#body.codeSnippets.group_7)
  - [JSON Mode](https://ai.google.dev/api/generate-content#body.codeSnippets.group_8)
  - [Code execution](https://ai.google.dev/api/generate-content#body.codeSnippets.group_9)
  - [Function Calling](https://ai.google.dev/api/generate-content#body.codeSnippets.group_10)
  - [Generation config](https://ai.google.dev/api/generate-content#body.codeSnippets.group_11)
  - [Safety Settings](https://ai.google.dev/api/generate-content#body.codeSnippets.group_12)
  - [System Instruction](https://ai.google.dev/api/generate-content#body.codeSnippets.group_13)

Generates a model response given an input `GenerateContentRequest`. Refer to the [text generation guide](https://ai.google.dev/gemini-api/docs/text-generation) for detailed usage information. Input capabilities differ between models, including tuned models. Refer to the [model guide](https://ai.google.dev/gemini-api/docs/models/gemini) and [tuning guide](https://ai.google.dev/gemini-api/docs/model-tuning) for details.

### Endpoint

post `https://generativelanguage.googleapis.com/v1beta/{model=models/*}:generateContent`   

### Path parameters

`model` `string` Required. The name of the `Model` to use for generating the completion.

Format: `models/{model}`. It takes the form `models/{model}`.

### Request body

The request body contains data with the following structure:
Fields `contents[]` ``object (`https://ai.google.dev/api/caching#Content`)`` Required. The content of the current conversation with the model.

For single-turn queries, this is a single instance. For multi-turn queries like [chat](https://ai.google.dev/gemini-api/docs/text-generation#chat), this is a repeated field that contains the conversation history and the latest request.
`tools[]` ``object (`https://ai.google.dev/api/caching#Tool`)`` Optional. A list of `Tools` the `Model` may use to generate the next response.

A `Tool` is a piece of code that enables the system to interact with external systems to perform an action, or set of actions, outside of knowledge and scope of the `Model`. Supported `Tool`s are `Function` and `codeExecution`. Refer to the [Function calling](https://ai.google.dev/gemini-api/docs/function-calling) and the [Code execution](https://ai.google.dev/gemini-api/docs/code-execution) guides to learn more.
`toolConfig` ``object (`https://ai.google.dev/api/caching#ToolConfig`)`` Optional. Tool configuration for any `Tool` specified in the request. Refer to the [Function calling guide](https://ai.google.dev/gemini-api/docs/function-calling#function_calling_mode) for a usage example.
`safetySettings[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.SafetySetting`)`` Optional. A list of unique `SafetySetting` instances for blocking unsafe content.

This will be enforced on the `GenerateContentRequest.contents` and `GenerateContentResponse.candidates`. There should not be more than one setting for each `SafetyCategory` type. The API will block any contents and responses that fail to meet the thresholds set by these settings. This list overrides the default settings for each `SafetyCategory` specified in the safetySettings. If there is no `SafetySetting` for a given `SafetyCategory` provided in the list, the API will use the default safety setting for that category. Harm categories HARM_CATEGORY_HATE_SPEECH, HARM_CATEGORY_SEXUALLY_EXPLICIT, HARM_CATEGORY_DANGEROUS_CONTENT, HARM_CATEGORY_HARASSMENT, HARM_CATEGORY_CIVIC_INTEGRITY are supported. Refer to the [guide](https://ai.google.dev/gemini-api/docs/safety-settings) for detailed information on available safety settings. Also refer to the [Safety guidance](https://ai.google.dev/gemini-api/docs/safety-guidance) to learn how to incorporate safety considerations in your AI applications.
`systemInstruction` ``object (`https://ai.google.dev/api/caching#Content`)`` Optional. Developer set [system instruction(s)](https://ai.google.dev/gemini-api/docs/system-instructions). Currently, text only.
`generationConfig` ``object (`https://ai.google.dev/api/generate-content#v1beta.GenerationConfig`)`` Optional. Configuration options for model generation and outputs.
`cachedContent` `string` Optional. The name of the content [cached](https://ai.google.dev/gemini-api/docs/caching) to use as context to serve the prediction. Format: `cachedContents/{cachedContent}`
`serviceTier` ``enum (`https://ai.google.dev/api/generate-content#v1beta.ServiceTier`)`` Optional. The service tier of the request.
`store` `boolean` Optional. Configures the logging behavior for a given request. If set, it takes precedence over the project-level logging config.

### Example request

### Text

### Python

    from google import genai

    client = genai.Client()
    response = client.models.generate_content(
        model="gemini-3.5-flash", contents="Write a story about a magic backpack."
    )
    print(response.text)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: "Write a story about a magic backpack.",
    });
    console.log(response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }
    contents := []*genai.Content{
    	genai.NewContentFromText("Write a story about a magic backpack.", genai.RoleUser),
    }
    response, err := client.Models.GenerateContent(ctx, "gemini-3.5-flash", contents, nil)
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Shell

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [{
            "parts":[{"text": "Write a story about a magic backpack."}]
            }]
           }' 2> /dev/null

### Java

    Client client = new Client();

    GenerateContentResponse response =
            client.models.generateContent(
                    "gemini-3.5-flash",
                    "Write a story about a magic backpack.",
                    null);

    System.out.println(response.text());

### Image

### Python

    from google import genai
    import PIL.Image

    client = genai.Client()
    organ = PIL.Image.open(media / "organ.jpg")
    response = client.models.generate_content(
        model="gemini-3.5-flash", contents=["Tell me about this instrument", organ]
    )
    print(response.text)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    const organ = await ai.files.upload({
      file: path.join(media, "organ.jpg"),
    });

    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: [
        createUserContent([
          "Tell me about this instrument", 
          createPartFromUri(organ.uri, organ.mimeType)
        ]),
      ],
    });
    console.log(response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "organ.jpg"), 
    	&genai.UploadFileConfig{
    		MIMEType : "image/jpeg",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }
    parts := []*genai.Part{
    	genai.NewPartFromText("Tell me about this instrument"),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }
    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }

    response, err := client.Models.GenerateContent(ctx, "gemini-3.5-flash", contents, nil)
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Shell

    # Use a temporary file to hold the base64 encoded image data
    TEMP_B64=$(mktemp)
    trap 'rm -f "$TEMP_B64"' EXIT
    base64 $B64FLAGS $IMG_PATH > "$TEMP_B64"

    # Use a temporary file to hold the JSON payload
    TEMP_JSON=$(mktemp)
    trap 'rm -f "$TEMP_JSON"' EXIT

    cat > "$TEMP_JSON" << EOF
    {
      "contents": [{
        "parts":[
          {"text": "Tell me about this instrument"},
          {
            "inline_data": {
              "mime_type":"image/jpeg",
              "data": "$(cat "$TEMP_B64")"
            }
          }
        ]
      }]
    }
    EOF

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d "@$TEMP_JSON" 2> /dev/null

### Java

    Client client = new Client();

    String path = media_path + "organ.jpg";
    byte[] imageData = Files.readAllBytes(Paths.get(path));

    Content content =
            Content.fromParts(
                    Part.fromText("Tell me about this instrument."),
                    Part.fromBytes(imageData, "image/jpeg"));

    GenerateContentResponse response = client.models.generateContent("gemini-3.5-flash", content, null);

    System.out.println(response.text());

### Audio

### Python

    from google import genai

    client = genai.Client()
    sample_audio = client.files.upload(file=media / "sample.mp3")
    response = client.models.generate_content(
        model="gemini-3.5-flash",
        contents=["Give me a summary of this audio file.", sample_audio],
    )
    print(response.text)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    const audio = await ai.files.upload({
      file: path.join(media, "sample.mp3"),
    });

    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: [
        createUserContent([
          "Give me a summary of this audio file.",
          createPartFromUri(audio.uri, audio.mimeType),
        ]),
      ],
    });
    console.log(response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "sample.mp3"), 
    	&genai.UploadFileConfig{
    		MIMEType : "audio/mpeg",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }

    parts := []*genai.Part{
    	genai.NewPartFromText("Give me a summary of this audio file."),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }

    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }

    response, err := client.Models.GenerateContent(ctx, "gemini-3.5-flash", contents, nil)
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Shell

    # Use File API to upload audio data to API request.
    MIME_TYPE=$(file -b --mime-type "${AUDIO_PATH}")
    NUM_BYTES=$(wc -c < "${AUDIO_PATH}")
    DISPLAY_NAME=AUDIO

    tmp_header_file=upload-header.tmp

    # Initial resumable request defining metadata.
    # The upload url is in the response headers dump them to a file.
    curl "${BASE_URL}/upload/v1beta/files?key=${GEMINI_API_KEY}" \
      -D upload-header.tmp \
      -H "X-Goog-Upload-Protocol: resumable" \
      -H "X-Goog-Upload-Command: start" \
      -H "X-Goog-Upload-Header-Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Header-Content-Type: ${MIME_TYPE}" \
      -H "Content-Type: application/json" \
      -d "{'file': {'display_name': '${DISPLAY_NAME}'}}" 2> /dev/null

    upload_url=$(grep -i "x-goog-upload-url: " "${tmp_header_file}" | cut -d" " -f2 | tr -d "\r")
    rm "${tmp_header_file}"

    # Upload the actual bytes.
    curl "${upload_url}" \
      -H "Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Offset: 0" \
      -H "X-Goog-Upload-Command: upload, finalize" \
      --data-binary "@${AUDIO_PATH}" 2> /dev/null > file_info.json

    file_uri=$(jq ".file.uri" file_info.json)
    echo file_uri=$file_uri

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [{
            "parts":[
              {"text": "Please describe this file."},
              {"file_data":{"mime_type": "audio/mpeg", "file_uri": '$file_uri'}}]
            }]
           }' 2> /dev/null > response.json

    cat response.json
    echo

    jq ".candidates[].content.parts[].text" response.json

### Video

### Python

    from google import genai
    import time

    client = genai.Client()
    # Video clip (CC BY 3.0) from https://peach.blender.org/download/
    myfile = client.files.upload(file=media / "Big_Buck_Bunny.mp4")
    print(f"{myfile=}")

    # Poll until the video file is completely processed (state becomes ACTIVE).
    while not myfile.state or myfile.state.name != "ACTIVE":
        print("Processing video...")
        print("File state:", myfile.state)
        time.sleep(5)
        myfile = client.files.get(name=myfile.name)

    response = client.models.generate_content(
        model="gemini-3.5-flash", contents=[myfile, "Describe this video clip"]
    )
    print(f"{response.text=}")

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    let video = await ai.files.upload({
      file: path.join(media, 'Big_Buck_Bunny.mp4'),
    });

    // Poll until the video file is completely processed (state becomes ACTIVE).
    while (!video.state || video.state.toString() !== 'ACTIVE') {
      console.log('Processing video...');
      console.log('File state: ', video.state);
      await sleep(5000);
      video = await ai.files.get({name: video.name});
    }

    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: [
        createUserContent([
          "Describe this video clip",
          createPartFromUri(video.uri, video.mimeType),
        ]),
      ],
    });
    console.log(response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "Big_Buck_Bunny.mp4"), 
    	&genai.UploadFileConfig{
    		MIMEType : "video/mp4",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }

    // Poll until the video file is completely processed (state becomes ACTIVE).
    for file.State == genai.FileStateUnspecified || file.State != genai.FileStateActive {
    	fmt.Println("Processing video...")
    	fmt.Println("File state:", file.State)
    	time.Sleep(5 * time.Second)

    	file, err = client.Files.Get(ctx, file.Name, nil)
    	if err != nil {
    		log.Fatal(err)
    	}
    }

    parts := []*genai.Part{
    	genai.NewPartFromText("Describe this video clip"),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }

    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }

    response, err := client.Models.GenerateContent(ctx, "gemini-3.5-flash", contents, nil)
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Shell

    # Use File API to upload audio data to API request.
    MIME_TYPE=$(file -b --mime-type "${VIDEO_PATH}")
    NUM_BYTES=$(wc -c < "${VIDEO_PATH}")
    DISPLAY_NAME=VIDEO

    # Initial resumable request defining metadata.
    # The upload url is in the response headers dump them to a file.
    curl "${BASE_URL}/upload/v1beta/files?key=${GEMINI_API_KEY}" \
      -D "${tmp_header_file}" \
      -H "X-Goog-Upload-Protocol: resumable" \
      -H "X-Goog-Upload-Command: start" \
      -H "X-Goog-Upload-Header-Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Header-Content-Type: ${MIME_TYPE}" \
      -H "Content-Type: application/json" \
      -d "{'file': {'display_name': '${DISPLAY_NAME}'}}" 2> /dev/null

    upload_url=$(grep -i "x-goog-upload-url: " "${tmp_header_file}" | cut -d" " -f2 | tr -d "\r")
    rm "${tmp_header_file}"

    # Upload the actual bytes.
    curl "${upload_url}" \
      -H "Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Offset: 0" \
      -H "X-Goog-Upload-Command: upload, finalize" \
      --data-binary "@${VIDEO_PATH}" 2> /dev/null > file_info.json

    file_uri=$(jq ".file.uri" file_info.json)
    echo file_uri=$file_uri

    state=$(jq ".file.state" file_info.json)
    echo state=$state

    name=$(jq ".file.name" file_info.json)
    echo name=$name

    while [[ "($state)" = *"PROCESSING"* ]];
    do
      echo "Processing video..."
      sleep 5
      # Get the file of interest to check state
      curl https://generativelanguage.googleapis.com/v1beta/files/$name > file_info.json
      state=$(jq ".file.state" file_info.json)
    done

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [{
            "parts":[
              {"text": "Transcribe the audio from this video, giving timestamps for salient events in the video. Also provide visual descriptions."},
              {"file_data":{"mime_type": "video/mp4", "file_uri": '$file_uri'}}]
            }]
           }' 2> /dev/null > response.json

    cat response.json
    echo

    jq ".candidates[].content.parts[].text" response.json

### PDF

### Python

    from google import genai

    client = genai.Client()
    sample_pdf = client.files.upload(file=media / "test.pdf")
    response = client.models.generate_content(
        model="gemini-3.5-flash",
        contents=["Give me a summary of this document:", sample_pdf],
    )
    print(f"{response.text=}")

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "test.pdf"), 
    	&genai.UploadFileConfig{
    		MIMEType : "application/pdf",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }

    parts := []*genai.Part{
    	genai.NewPartFromText("Give me a summary of this document:"),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }

    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }

    response, err := client.Models.GenerateContent(ctx, "gemini-3.5-flash", contents, nil)
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Shell

    MIME_TYPE=$(file -b --mime-type "${PDF_PATH}")
    NUM_BYTES=$(wc -c < "${PDF_PATH}")
    DISPLAY_NAME=TEXT


    echo $MIME_TYPE
    tmp_header_file=upload-header.tmp

    # Initial resumable request defining metadata.
    # The upload url is in the response headers dump them to a file.
    curl "${BASE_URL}/upload/v1beta/files?key=${GEMINI_API_KEY}" \
      -D upload-header.tmp \
      -H "X-Goog-Upload-Protocol: resumable" \
      -H "X-Goog-Upload-Command: start" \
      -H "X-Goog-Upload-Header-Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Header-Content-Type: ${MIME_TYPE}" \
      -H "Content-Type: application/json" \
      -d "{'file': {'display_name': '${DISPLAY_NAME}'}}" 2> /dev/null

    upload_url=$(grep -i "x-goog-upload-url: " "${tmp_header_file}" | cut -d" " -f2 | tr -d "\r")
    rm "${tmp_header_file}"

    # Upload the actual bytes.
    curl "${upload_url}" \
      -H "Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Offset: 0" \
      -H "X-Goog-Upload-Command: upload, finalize" \
      --data-binary "@${PDF_PATH}" 2> /dev/null > file_info.json

    file_uri=$(jq ".file.uri" file_info.json)
    echo file_uri=$file_uri

    # Now generate content using that file
    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [{
            "parts":[
              {"text": "Can you add a few more lines to this poem?"},
              {"file_data":{"mime_type": "application/pdf", "file_uri": '$file_uri'}}]
            }]
           }' 2> /dev/null > response.json

    cat response.json
    echo

    jq ".candidates[].content.parts[].text" response.json

### Chat

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()
    # Pass initial history using the "history" argument
    chat = client.chats.create(
        model="gemini-3.5-flash",
        history=[
            types.Content(role="user", parts=[types.Part(text="Hello")]),
            types.Content(
                role="model",
                parts=[
                    types.Part(
                        text="Great to meet you. What would you like to know?"
                    )
                ],
            ),
        ],
    )
    response = chat.send_message(message="I have 2 dogs in my house.")
    print(response.text)
    response = chat.send_message(message="How many paws are in my house?")
    print(response.text)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
    const chat = ai.chats.create({
      model: "gemini-3.5-flash",
      history: [
        {
          role: "user",
          parts: [{ text: "Hello" }],
        },
        {
          role: "model",
          parts: [{ text: "Great to meet you. What would you like to know?" }],
        },
      ],
    });

    const response1 = await chat.sendMessage({
      message: "I have 2 dogs in my house.",
    });
    console.log("Chat response 1:", response1.text);

    const response2 = await chat.sendMessage({
      message: "How many paws are in my house?",
    });
    console.log("Chat response 2:", response2.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    // Pass initial history using the History field.
    history := []*genai.Content{
    	genai.NewContentFromText("Hello", genai.RoleUser),
    	genai.NewContentFromText("Great to meet you. What would you like to know?", genai.RoleModel),
    }

    chat, err := client.Chats.Create(ctx, "gemini-3.5-flash", nil, history)
    if err != nil {
    	log.Fatal(err)
    }

    firstResp, err := chat.SendMessage(ctx, genai.Part{Text: "I have 2 dogs in my house."})
    if err != nil {
    	log.Fatal(err)
    }
    fmt.Println(firstResp.Text())

    secondResp, err := chat.SendMessage(ctx, genai.Part{Text: "How many paws are in my house?"})
    if err != nil {
    	log.Fatal(err)
    }
    fmt.Println(secondResp.Text())

### Shell

    curl https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [
            {"role":"user",
             "parts":[{
               "text": "Hello"}]},
            {"role": "model",
             "parts":[{
               "text": "Great to meet you. What would you like to know?"}]},
            {"role":"user",
             "parts":[{
               "text": "I have two dogs in my house. How many paws are in my house?"}]},
          ]
        }' 2> /dev/null | grep "text"

### Java

    Client client = new Client();

    Content userContent = Content.fromParts(Part.fromText("Hello"));
    Content modelContent =
            Content.builder()
                    .role("model")
                    .parts(
                            Collections.singletonList(
                                    Part.fromText("Great to meet you. What would you like to know?")
                            )
                    ).build();

    Chat chat = client.chats.create(
            "gemini-3.5-flash",
            GenerateContentConfig.builder()
                    .systemInstruction(userContent)
                    .systemInstruction(modelContent)
                    .build()
    );

    GenerateContentResponse response1 = chat.sendMessage("I have 2 dogs in my house.");
    System.out.println(response1.text());

    GenerateContentResponse response2 = chat.sendMessage("How many paws are in my house?");
    System.out.println(response2.text());

### Cache

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()
    document = client.files.upload(file=media / "a11.txt")
    model_name = "gemini-3.5-flash"

    cache = client.caches.create(
        model=model_name,
        config=types.CreateCachedContentConfig(
            contents=[document],
            system_instruction="You are an expert analyzing transcripts.",
        ),
    )
    print(cache)

    response = client.models.generate_content(
        model=model_name,
        contents="Please summarize this transcript",
        config=types.GenerateContentConfig(cached_content=cache.name),
    )
    print(response.text)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
    const filePath = path.join(media, "a11.txt");
    const document = await ai.files.upload({
      file: filePath,
      config: { mimeType: "text/plain" },
    });
    console.log("Uploaded file name:", document.name);
    const modelName = "gemini-3.5-flash";

    const contents = [
      createUserContent(createPartFromUri(document.uri, document.mimeType)),
    ];

    const cache = await ai.caches.create({
      model: modelName,
      config: {
        contents: contents,
        systemInstruction: "You are an expert analyzing transcripts.",
      },
    });
    console.log("Cache created:", cache);

    const response = await ai.models.generateContent({
      model: modelName,
      contents: "Please summarize this transcript",
      config: { cachedContent: cache.name },
    });
    console.log("Response text:", response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"), 
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    modelName := "gemini-3.5-flash"
    document, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "a11.txt"), 
    	&genai.UploadFileConfig{
    		MIMEType : "text/plain",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }
    parts := []*genai.Part{
    	genai.NewPartFromURI(document.URI, document.MIMEType),
    }
    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }
    cache, err := client.Caches.Create(ctx, modelName, &genai.CreateCachedContentConfig{
    	Contents: contents,
    	SystemInstruction: genai.NewContentFromText(
    		"You are an expert analyzing transcripts.", genai.RoleUser,
    	),
    })
    if err != nil {
    	log.Fatal(err)
    }
    fmt.Println("Cache created:")
    fmt.Println(cache)

    // Use the cache for generating content.
    response, err := client.Models.GenerateContent(
    	ctx,
    	modelName,
    	genai.Text("Please summarize this transcript"),
    	&genai.GenerateContentConfig{
    		CachedContent: cache.Name,
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Tuned Model

### Python

    # With Gemini 2 we're launching a new SDK. See the following doc for details.
    # https://ai.google.dev/gemini-api/docs/migrate

### JSON Mode

### Python

    from google import genai
    from google.genai import types
    from typing_extensions import TypedDict

    class Recipe(TypedDict):
        recipe_name: str
        ingredients: list[str]

    client = genai.Client()
    result = client.models.generate_content(
        model="gemini-3.5-flash",
        contents="List a few popular cookie recipes.",
        config=types.GenerateContentConfig(
            response_mime_type="application/json", response_schema=list[Recipe]
        ),
    )
    print(result)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: "List a few popular cookie recipes.",
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: "array",
          items: {
            type: "object",
            properties: {
              recipeName: { type: "string" },
              ingredients: { type: "array", items: { type: "string" } },
            },
            required: ["recipeName", "ingredients"],
          },
        },
      },
    });
    console.log(response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"), 
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    schema := &genai.Schema{
    	Type: genai.TypeArray,
    	Items: &genai.Schema{
    		Type: genai.TypeObject,
    		Properties: map[string]*genai.Schema{
    			"recipe_name": {Type: genai.TypeString},
    			"ingredients": {
    				Type:  genai.TypeArray,
    				Items: &genai.Schema{Type: genai.TypeString},
    			},
    		},
    		Required: []string{"recipe_name"},
    	},
    }

    config := &genai.GenerateContentConfig{
    	ResponseMIMEType: "application/json",
    	ResponseSchema:   schema,
    }

    response, err := client.Models.GenerateContent(
    	ctx,
    	"gemini-3.5-flash",
    	genai.Text("List a few popular cookie recipes."),
    	config,
    )
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Shell

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
    -H 'Content-Type: application/json' \
    -d '{
        "contents": [{
          "parts":[
            {"text": "List 5 popular cookie recipes"}
            ]
        }],
        "generationConfig": {
            "response_mime_type": "application/json",
            "response_schema": {
              "type": "ARRAY",
              "items": {
                "type": "OBJECT",
                "properties": {
                  "recipe_name": {"type":"STRING"},
                }
              }
            }
        }
    }' 2> /dev/null | head

### Java

    Client client = new Client();

    Schema recipeSchema = Schema.builder()
            .type(Array.class.getSimpleName())
            .items(Schema.builder()
                    .type(Object.class.getSimpleName())
                    .properties(
                            Map.of("recipe_name", Schema.builder()
                                            .type(String.class.getSimpleName())
                                            .build(),
                                    "ingredients", Schema.builder()
                                            .type(Array.class.getSimpleName())
                                            .items(Schema.builder()
                                                    .type(String.class.getSimpleName())
                                                    .build())
                                            .build())
                    )
                    .required(List.of("recipe_name", "ingredients"))
                    .build())
            .build();

    GenerateContentConfig config =
            GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(recipeSchema)
                    .build();

    GenerateContentResponse response =
            client.models.generateContent(
                    "gemini-3.5-flash",
                    "List a few popular cookie recipes.",
                    config);

    System.out.println(response.text());

### Code execution

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()
    response = client.models.generate_content(
        model="gemini-3.5-flash",
        contents=(
            "Write and execute code that calculates the sum of the first 50 prime numbers. "
            "Ensure that only the executable code and its resulting output are generated."
        ),
    )
    # Each part may contain text, executable code, or an execution result.
    for part in response.candidates[0].content.parts:
        print(part, "\n")

    print("-" * 80)
    # The .text accessor concatenates the parts into a markdown-formatted text.
    print("\n", response.text)

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    response, err := client.Models.GenerateContent(
    	ctx,
    	"gemini-3.5-flash",
    	genai.Text(
    		`Write and execute code that calculates the sum of the first 50 prime numbers.
    		 Ensure that only the executable code and its resulting output are generated.`,
    	),
    	&genai.GenerateContentConfig{},
    )
    if err != nil {
    	log.Fatal(err)
    }

    // Print the response.
    printResponse(response)

    fmt.Println("---")
    fmt.Println(response.Text())

### Java

    Client client = new Client();

    String prompt = """
            Write and execute code that calculates the sum of the first 50 prime numbers.
            Ensure that only the executable code and its resulting output are generated.
            """;

    GenerateContentResponse response =
            client.models.generateContent(
                    "gemini-3.5-flash",
                    prompt,
                    null);

    for (Part part : response.candidates().get().getFirst().content().get().parts().get()) {
        System.out.println(part + "\n");
    }

    System.out.println("-".repeat(80));
    System.out.println(response.text());

### Function Calling

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()

    def add(a: float, b: float) -> float:
        """returns a + b."""
        return a + b

    def subtract(a: float, b: float) -> float:
        """returns a - b."""
        return a - b

    def multiply(a: float, b: float) -> float:
        """returns a * b."""
        return a * b

    def divide(a: float, b: float) -> float:
        """returns a / b."""
        return a / b

    # Create a chat session; function calling (via tools) is enabled in the config.
    chat = client.chats.create(
        model="gemini-3.5-flash",
        config=types.GenerateContentConfig(tools=[add, subtract, multiply, divide]),
    )
    response = chat.send_message(
        message="I have 57 cats, each owns 44 mittens, how many mittens is that in total?"
    )
    print(response.text)

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }
    modelName := "gemini-3.5-flash"

    // Create the function declarations for arithmetic operations.
    addDeclaration := createArithmeticToolDeclaration("addNumbers", "Return the result of adding two numbers.")
    subtractDeclaration := createArithmeticToolDeclaration("subtractNumbers", "Return the result of subtracting the second number from the first.")
    multiplyDeclaration := createArithmeticToolDeclaration("multiplyNumbers", "Return the product of two numbers.")
    divideDeclaration := createArithmeticToolDeclaration("divideNumbers", "Return the quotient of dividing the first number by the second.")

    // Group the function declarations as a tool.
    tools := []*genai.Tool{
    	{
    		FunctionDeclarations: []*genai.FunctionDeclaration{
    			addDeclaration,
    			subtractDeclaration,
    			multiplyDeclaration,
    			divideDeclaration,
    		},
    	},
    }

    // Create the content prompt.
    contents := []*genai.Content{
    	genai.NewContentFromText(
    		"I have 57 cats, each owns 44 mittens, how many mittens is that in total?", genai.RoleUser,
    	),
    }

    // Set up the generate content configuration with function calling enabled.
    config := &genai.GenerateContentConfig{
    	Tools: tools,
    	ToolConfig: &genai.ToolConfig{
    		FunctionCallingConfig: &genai.FunctionCallingConfig{
    			// The mode equivalent to FunctionCallingConfigMode.ANY in JS.
    			Mode: genai.FunctionCallingConfigModeAny,
    		},
    	},
    }

    genContentResp, err := client.Models.GenerateContent(ctx, modelName, contents, config)
    if err != nil {
    	log.Fatal(err)
    }

    // Assume the response includes a list of function calls.
    if len(genContentResp.FunctionCalls()) == 0 {
    	log.Println("No function call returned from the AI.")
    	return nil
    }
    functionCall := genContentResp.FunctionCalls()[0]
    log.Printf("Function call: %+v\n", functionCall)

    // Marshal the Args map into JSON bytes.
    argsMap, err := json.Marshal(functionCall.Args)
    if err != nil {
    	log.Fatal(err)
    }

    // Unmarshal the JSON bytes into the ArithmeticArgs struct.
    var args ArithmeticArgs
    if err := json.Unmarshal(argsMap, &args); err != nil {
    	log.Fatal(err)
    }

    // Map the function name to the actual arithmetic function.
    var result float64
    switch functionCall.Name {
    	case "addNumbers":
    		result = add(args.FirstParam, args.SecondParam)
    	case "subtractNumbers":
    		result = subtract(args.FirstParam, args.SecondParam)
    	case "multiplyNumbers":
    		result = multiply(args.FirstParam, args.SecondParam)
    	case "divideNumbers":
    		result = divide(args.FirstParam, args.SecondParam)
    	default:
    		return fmt.Errorf("unimplemented function: %s", functionCall.Name)
    }
    log.Printf("Function result: %v\n", result)

    // Prepare the final result message as content.
    resultContents := []*genai.Content{
    	genai.NewContentFromText("The final result is " + fmt.Sprintf("%v", result), genai.RoleUser),
    }

    // Use GenerateContent to send the final result.
    finalResponse, err := client.Models.GenerateContent(ctx, modelName, resultContents, &genai.GenerateContentConfig{})
    if err != nil {
    	log.Fatal(err)
    }

    printResponse(finalResponse)

### Node.js

      // Make sure to include the following import:
      // import {GoogleGenAI} from '@google/genai';
      const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

      /**
       * The add function returns the sum of two numbers.
       * @param {number} a
       * @param {number} b
       * @returns {number}
       */
      function add(a, b) {
        return a + b;
      }

      /**
       * The subtract function returns the difference (a - b).
       * @param {number} a
       * @param {number} b
       * @returns {number}
       */
      function subtract(a, b) {
        return a - b;
      }

      /**
       * The multiply function returns the product of two numbers.
       * @param {number} a
       * @param {number} b
       * @returns {number}
       */
      function multiply(a, b) {
        return a * b;
      }

      /**
       * The divide function returns the quotient of a divided by b.
       * @param {number} a
       * @param {number} b
       * @returns {number}
       */
      function divide(a, b) {
        return a / b;
      }

      const addDeclaration = {
        name: "addNumbers",
        parameters: {
          type: "object",
          description: "Return the result of adding two numbers.",
          properties: {
            firstParam: {
              type: "number",
              description:
                "The first parameter which can be an integer or a floating point number.",
            },
            secondParam: {
              type: "number",
              description:
                "The second parameter which can be an integer or a floating point number.",
            },
          },
          required: ["firstParam", "secondParam"],
        },
      };

      const subtractDeclaration = {
        name: "subtractNumbers",
        parameters: {
          type: "object",
          description:
            "Return the result of subtracting the second number from the first.",
          properties: {
            firstParam: {
              type: "number",
              description: "The first parameter.",
            },
            secondParam: {
              type: "number",
              description: "The second parameter.",
            },
          },
          required: ["firstParam", "secondParam"],
        },
      };

      const multiplyDeclaration = {
        name: "multiplyNumbers",
        parameters: {
          type: "object",
          description: "Return the product of two numbers.",
          properties: {
            firstParam: {
              type: "number",
              description: "The first parameter.",
            },
            secondParam: {
              type: "number",
              description: "The second parameter.",
            },
          },
          required: ["firstParam", "secondParam"],
        },
      };

      const divideDeclaration = {
        name: "divideNumbers",
        parameters: {
          type: "object",
          description:
            "Return the quotient of dividing the first number by the second.",
          properties: {
            firstParam: {
              type: "number",
              description: "The first parameter.",
            },
            secondParam: {
              type: "number",
              description: "The second parameter.",
            },
          },
          required: ["firstParam", "secondParam"],
        },
      };

      // Step 1: Call generateContent with function calling enabled.
      const generateContentResponse = await ai.models.generateContent({
        model: "gemini-3.5-flash",
        contents:
          "I have 57 cats, each owns 44 mittens, how many mittens is that in total?",
        config: {
          toolConfig: {
            functionCallingConfig: {
              mode: FunctionCallingConfigMode.ANY,
            },
          },
          tools: [
            {
              functionDeclarations: [
                addDeclaration,
                subtractDeclaration,
                multiplyDeclaration,
                divideDeclaration,
              ],
            },
          ],
        },
      });

      // Step 2: Extract the function call.(
      // Assuming the response contains a 'functionCalls' array.
      const functionCall =
        generateContentResponse.functionCalls &&
        generateContentResponse.functionCalls[0];
      console.log(functionCall);

      // Parse the arguments.
      const args = functionCall.args;
      // Expected args format: { firstParam: number, secondParam: number }

      // Step 3: Invoke the actual function based on the function name.
      const functionMapping = {
        addNumbers: add,
        subtractNumbers: subtract,
        multiplyNumbers: multiply,
        divideNumbers: divide,
      };
      const func = functionMapping[functionCall.name];
      if (!func) {
        console.error("Unimplemented error:", functionCall.name);
        return generateContentResponse;
      }
      const resultValue = func(args.firstParam, args.secondParam);
      console.log("Function result:", resultValue);

      // Step 4: Use the chat API to send the result as the final answer.
      const chat = ai.chats.create({ model: "gemini-3.5-flash" });
      const chatResponse = await chat.sendMessage({
        message: "The final result is " + resultValue,
      });
      console.log(chatResponse.text);
      return chatResponse;
    }

### Shell


    cat > tools.json << EOF
    {
      "function_declarations": [
        {
          "name": "enable_lights",
          "description": "Turn on the lighting system."
        },
        {
          "name": "set_light_color",
          "description": "Set the light color. Lights must be enabled for this to work.",
          "parameters": {
            "type": "object",
            "properties": {
              "rgb_hex": {
                "type": "string",
                "description": "The light color as a 6-digit hex string, e.g. ff0000 for red."
              }
            },
            "required": [
              "rgb_hex"
            ]
          }
        },
        {
          "name": "stop_lights",
          "description": "Turn off the lighting system."
        }
      ]
    } 
    EOF

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
      -H 'Content-Type: application/json' \
      -d @<(echo '
      {
        "system_instruction": {
          "parts": {
            "text": "You are a helpful lighting system bot. You can turn lights on and off, and you can set the color. Do not perform any other tasks."
          }
        },
        "tools": ['$(cat tools.json)'],

        "tool_config": {
          "function_calling_config": {"mode": "auto"}
        },

        "contents": {
          "role": "user",
          "parts": {
            "text": "Turn on the lights please."
          }
        }
      }
    ') 2>/dev/null |sed -n '/"content"/,/"finishReason"/p'

### Java

    Client client = new Client();

    FunctionDeclaration addFunction =
            FunctionDeclaration.builder()
                    .name("addNumbers")
                    .parameters(
                            Schema.builder()
                                    .type("object")
                                    .properties(Map.of(
                                            "firstParam", Schema.builder().type("number").description("First number").build(),
                                            "secondParam", Schema.builder().type("number").description("Second number").build()))
                                    .required(Arrays.asList("firstParam", "secondParam"))
                                    .build())
                    .build();

    FunctionDeclaration subtractFunction =
            FunctionDeclaration.builder()
                    .name("subtractNumbers")
                    .parameters(
                            Schema.builder()
                                    .type("object")
                                    .properties(Map.of(
                                            "firstParam", Schema.builder().type("number").description("First number").build(),
                                            "secondParam", Schema.builder().type("number").description("Second number").build()))
                                    .required(Arrays.asList("firstParam", "secondParam"))
                                    .build())
                    .build();

    FunctionDeclaration multiplyFunction =
            FunctionDeclaration.builder()
                    .name("multiplyNumbers")
                    .parameters(
                            Schema.builder()
                                    .type("object")
                                    .properties(Map.of(
                                            "firstParam", Schema.builder().type("number").description("First number").build(),
                                            "secondParam", Schema.builder().type("number").description("Second number").build()))
                                    .required(Arrays.asList("firstParam", "secondParam"))
                                    .build())
                    .build();

    FunctionDeclaration divideFunction =
            FunctionDeclaration.builder()
                    .name("divideNumbers")
                    .parameters(
                            Schema.builder()
                                    .type("object")
                                    .properties(Map.of(
                                            "firstParam", Schema.builder().type("number").description("First number").build(),
                                            "secondParam", Schema.builder().type("number").description("Second number").build()))
                                    .required(Arrays.asList("firstParam", "secondParam"))
                                    .build())
                    .build();

    GenerateContentConfig config = GenerateContentConfig.builder()
            .toolConfig(ToolConfig.builder().functionCallingConfig(
                    FunctionCallingConfig.builder().mode("ANY").build()
            ).build())
            .tools(
                    Collections.singletonList(
                            Tool.builder().functionDeclarations(
                                    Arrays.asList(
                                            addFunction,
                                            subtractFunction,
                                            divideFunction,
                                            multiplyFunction
                                    )
                            ).build()

                    )
            )
            .build();

    GenerateContentResponse response =
            client.models.generateContent(
                    "gemini-3.5-flash",
                    "I have 57 cats, each owns 44 mittens, how many mittens is that in total?",
                    config);


    if (response.functionCalls() == null || response.functionCalls().isEmpty()) {
        System.err.println("No function call received");
        return null;
    }

    var functionCall = response.functionCalls().getFirst();
    String functionName = functionCall.name().get();
    var arguments = functionCall.args();

    Map<String, BiFunction<Double, Double, Double>> functionMapping = new HashMap<>();
    functionMapping.put("addNumbers", (a, b) -> a + b);
    functionMapping.put("subtractNumbers", (a, b) -> a - b);
    functionMapping.put("multiplyNumbers", (a, b) -> a * b);
    functionMapping.put("divideNumbers", (a, b) -> b != 0 ? a / b : Double.NaN);

    BiFunction<Double, Double, Double> function = functionMapping.get(functionName);

    Number firstParam = (Number) arguments.get().get("firstParam");
    Number secondParam = (Number) arguments.get().get("secondParam");
    Double result = function.apply(firstParam.doubleValue(), secondParam.doubleValue());

    System.out.println(result);

### Generation config

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()
    response = client.models.generate_content(
        model="gemini-3.5-flash",
        contents="Tell me a story about a magic backpack.",
        config=types.GenerateContentConfig(
            candidate_count=1,
            stop_sequences=["x"],
            max_output_tokens=20,
            temperature=1.0,
        ),
    )
    print(response.text)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: "Tell me a story about a magic backpack.",
      config: {
        candidateCount: 1,
        stopSequences: ["x"],
        maxOutputTokens: 20,
        temperature: 1.0,
      },
    });

    console.log(response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    // Create local variables for parameters.
    candidateCount := int32(1)
    maxOutputTokens := int32(20)
    temperature := float32(1.0)

    response, err := client.Models.GenerateContent(
    	ctx,
    	"gemini-3.5-flash",
    	genai.Text("Tell me a story about a magic backpack."),
    	&genai.GenerateContentConfig{
    		CandidateCount:  candidateCount,
    		StopSequences:   []string{"x"},
    		MaxOutputTokens: maxOutputTokens,
    		Temperature:     &temperature,
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }

    printResponse(response)

### Shell

    curl https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
            "contents": [{
                "parts":[
                    {"text": "Explain how AI works"}
                ]
            }],
            "generationConfig": {
                "stopSequences": [
                    "Title"
                ],
                "temperature": 1.0,
                "maxOutputTokens": 800,
                "topP": 0.8,
                "topK": 10
            }
        }'  2> /dev/null | grep "text"

### Java

    Client client = new Client();

    GenerateContentConfig config =
            GenerateContentConfig.builder()
                    .candidateCount(1)
                    .stopSequences(List.of("x"))
                    .maxOutputTokens(20)
                    .temperature(1.0F)
                    .build();

    GenerateContentResponse response =
            client.models.generateContent(
                    "gemini-3.5-flash",
                    "Tell me a story about a magic backpack.",
                    config);

    System.out.println(response.text());

### Safety Settings

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()
    unsafe_prompt = (
        "I support Martians Soccer Club and I think Jupiterians Football Club sucks! "
        "Write a ironic phrase about them including expletives."
    )
    response = client.models.generate_content(
        model="gemini-3.5-flash",
        contents=unsafe_prompt,
        config=types.GenerateContentConfig(
            safety_settings=[
                types.SafetySetting(
                    category="HARM_CATEGORY_HATE_SPEECH",
                    threshold="BLOCK_MEDIUM_AND_ABOVE",
                ),
                types.SafetySetting(
                    category="HARM_CATEGORY_HARASSMENT", threshold="BLOCK_ONLY_HIGH"
                ),
            ]
        ),
    )
    try:
        print(response.text)
    except Exception:
        print("No information generated by the model.")

    print(response.candidates[0].safety_ratings)

### Node.js

      // Make sure to include the following import:
      // import {GoogleGenAI} from '@google/genai';
      const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
      const unsafePrompt =
        "I support Martians Soccer Club and I think Jupiterians Football Club sucks! Write a ironic phrase about them including expletives.";

      const response = await ai.models.generateContent({
        model: "gemini-3.5-flash",
        contents: unsafePrompt,
        config: {
          safetySettings: [
            {
              category: "HARM_CATEGORY_HATE_SPEECH",
              threshold: "BLOCK_MEDIUM_AND_ABOVE",
            },
            {
              category: "HARM_CATEGORY_HARASSMENT",
              threshold: "BLOCK_ONLY_HIGH",
            },
          ],
        },
      });

      try {
        console.log("Generated text:", response.text);
      } catch (error) {
        console.log("No information generated by the model.");
      }
      console.log("Safety ratings:", response.candidates[0].safetyRatings);
      return response;
    }

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    unsafePrompt := "I support Martians Soccer Club and I think Jupiterians Football Club sucks! " +
    	"Write a ironic phrase about them including expletives."

    config := &genai.GenerateContentConfig{
    	SafetySettings: []*genai.SafetySetting{
    		{
    			Category:  "HARM_CATEGORY_HATE_SPEECH",
    			Threshold: "BLOCK_MEDIUM_AND_ABOVE",
    		},
    		{
    			Category:  "HARM_CATEGORY_HARASSMENT",
    			Threshold: "BLOCK_ONLY_HIGH",
    		},
    	},
    }
    contents := []*genai.Content{
    	genai.NewContentFromText(unsafePrompt, genai.RoleUser),
    }
    response, err := client.Models.GenerateContent(ctx, "gemini-3.5-flash", contents, config)
    if err != nil {
    	log.Fatal(err)
    }

    // Print the generated text.
    text := response.Text()
    fmt.Println("Generated text:", text)

    // Print the and safety ratings from the first candidate.
    if len(response.Candidates) > 0 {
    	fmt.Println("Finish reason:", response.Candidates[0].FinishReason)
    	safetyRatings, err := json.MarshalIndent(response.Candidates[0].SafetyRatings, "", "  ")
    	if err != nil {
    		return err
    	}
    	fmt.Println("Safety ratings:", string(safetyRatings))
    } else {
    	fmt.Println("No candidate returned.")
    }

### Shell

    echo '{
        "safetySettings": [
            {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_ONLY_HIGH"},
            {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_MEDIUM_AND_ABOVE"}
        ],
        "contents": [{
            "parts":[{
                "text": "'I support Martians Soccer Club and I think Jupiterians Football Club sucks! Write a ironic phrase about them.'"}]}]}' > request.json

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d @request.json 2> /dev/null

### Java

    Client client = new Client();

    String unsafePrompt = """
             I support Martians Soccer Club and I think Jupiterians Football Club sucks!
             Write a ironic phrase about them including expletives.
            """;

    GenerateContentConfig config =
            GenerateContentConfig.builder()
                    .safetySettings(Arrays.asList(
                            SafetySetting.builder()
                                    .category("HARM_CATEGORY_HATE_SPEECH")
                                    .threshold("BLOCK_MEDIUM_AND_ABOVE")
                                    .build(),
                            SafetySetting.builder()
                                    .category("HARM_CATEGORY_HARASSMENT")
                                    .threshold("BLOCK_ONLY_HIGH")
                                    .build()
                    )).build();

    GenerateContentResponse response =
            client.models.generateContent(
                    "gemini-3.5-flash",
                    unsafePrompt,
                    config);

    try {
        System.out.println(response.text());
    } catch (Exception e) {
        System.out.println("No information generated by the model");
    }

    System.out.println(response.candidates().get().getFirst().safetyRatings());

### System Instruction

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()
    response = client.models.generate_content(
        model="gemini-3.5-flash",
        contents="Good morning! How are you?",
        config=types.GenerateContentConfig(
            system_instruction="You are a cat. Your name is Neko."
        ),
    )
    print(response.text)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: "Good morning! How are you?",
      config: {
        systemInstruction: "You are a cat. Your name is Neko.",
      },
    });
    console.log(response.text);

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    // Construct the user message contents.
    contents := []*genai.Content{
    	genai.NewContentFromText("Good morning! How are you?", genai.RoleUser),
    }

    // Set the system instruction as a *genai.Content.
    config := &genai.GenerateContentConfig{
    	SystemInstruction: genai.NewContentFromText("You are a cat. Your name is Neko.", genai.RoleUser),
    }

    response, err := client.Models.GenerateContent(ctx, "gemini-3.5-flash", contents, config)
    if err != nil {
    	log.Fatal(err)
    }
    printResponse(response)

### Shell

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
    -H 'Content-Type: application/json' \
    -d '{ "system_instruction": {
        "parts":
          { "text": "You are a cat. Your name is Neko."}},
        "contents": {
          "parts": {
            "text": "Hello there"}}}'

### Java

    Client client = new Client();

    Part textPart = Part.builder().text("You are a cat. Your name is Neko.").build();

    Content content = Content.builder().role("system").parts(ImmutableList.of(textPart)).build();

    GenerateContentConfig config = GenerateContentConfig.builder()
            .systemInstruction(content)
            .build();

    GenerateContentResponse response =
            client.models.generateContent(
                    "gemini-3.5-flash",
                    "Good morning! How are you?",
                    config);

    System.out.println(response.text());

### Response body

If successful, the response body contains an instance of `https://ai.google.dev/api/generate-content#v1beta.GenerateContentResponse`.

## Method: models.streamGenerateContent

- [Endpoint](https://ai.google.dev/api/generate-content#body.HTTP_TEMPLATE)
- [Path parameters](https://ai.google.dev/api/generate-content#body.PATH_PARAMETERS)
- [Request body](https://ai.google.dev/api/generate-content#body.request_body)
  - [JSON representation](https://ai.google.dev/api/generate-content#body.request_body.SCHEMA_REPRESENTATION)
- [Response body](https://ai.google.dev/api/generate-content#body.response_body)
- [Authorization scopes](https://ai.google.dev/api/generate-content#body.aspect)
- [Example request](https://ai.google.dev/api/generate-content#body.codeSnippets)
  - [Text](https://ai.google.dev/api/generate-content#body.codeSnippets.group)
  - [Image](https://ai.google.dev/api/generate-content#body.codeSnippets.group_1)
  - [Audio](https://ai.google.dev/api/generate-content#body.codeSnippets.group_2)
  - [Video](https://ai.google.dev/api/generate-content#body.codeSnippets.group_3)
  - [PDF](https://ai.google.dev/api/generate-content#body.codeSnippets.group_4)
  - [Chat](https://ai.google.dev/api/generate-content#body.codeSnippets.group_5)

Generates a [streamed response](https://ai.google.dev/gemini-api/docs/text-generation?lang=python#generate-a-text-stream) from the model given an input `GenerateContentRequest`.

### Endpoint

post `https://generativelanguage.googleapis.com/v1beta/{model=models/*}:streamGenerateContent`   

### Path parameters

`model` `string` Required. The name of the `Model` to use for generating the completion.

Format: `models/{model}`. It takes the form `models/{model}`.

### Request body

The request body contains data with the following structure:
Fields `contents[]` ``object (`https://ai.google.dev/api/caching#Content`)`` Required. The content of the current conversation with the model.

For single-turn queries, this is a single instance. For multi-turn queries like [chat](https://ai.google.dev/gemini-api/docs/text-generation#chat), this is a repeated field that contains the conversation history and the latest request.
`tools[]` ``object (`https://ai.google.dev/api/caching#Tool`)`` Optional. A list of `Tools` the `Model` may use to generate the next response.

A `Tool` is a piece of code that enables the system to interact with external systems to perform an action, or set of actions, outside of knowledge and scope of the `Model`. Supported `Tool`s are `Function` and `codeExecution`. Refer to the [Function calling](https://ai.google.dev/gemini-api/docs/function-calling) and the [Code execution](https://ai.google.dev/gemini-api/docs/code-execution) guides to learn more.
`toolConfig` ``object (`https://ai.google.dev/api/caching#ToolConfig`)`` Optional. Tool configuration for any `Tool` specified in the request. Refer to the [Function calling guide](https://ai.google.dev/gemini-api/docs/function-calling#function_calling_mode) for a usage example.
`safetySettings[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.SafetySetting`)`` Optional. A list of unique `SafetySetting` instances for blocking unsafe content.

This will be enforced on the `GenerateContentRequest.contents` and `GenerateContentResponse.candidates`. There should not be more than one setting for each `SafetyCategory` type. The API will block any contents and responses that fail to meet the thresholds set by these settings. This list overrides the default settings for each `SafetyCategory` specified in the safetySettings. If there is no `SafetySetting` for a given `SafetyCategory` provided in the list, the API will use the default safety setting for that category. Harm categories HARM_CATEGORY_HATE_SPEECH, HARM_CATEGORY_SEXUALLY_EXPLICIT, HARM_CATEGORY_DANGEROUS_CONTENT, HARM_CATEGORY_HARASSMENT, HARM_CATEGORY_CIVIC_INTEGRITY are supported. Refer to the [guide](https://ai.google.dev/gemini-api/docs/safety-settings) for detailed information on available safety settings. Also refer to the [Safety guidance](https://ai.google.dev/gemini-api/docs/safety-guidance) to learn how to incorporate safety considerations in your AI applications.
`systemInstruction` ``object (`https://ai.google.dev/api/caching#Content`)`` Optional. Developer set [system instruction(s)](https://ai.google.dev/gemini-api/docs/system-instructions). Currently, text only.
`generationConfig` ``object (`https://ai.google.dev/api/generate-content#v1beta.GenerationConfig`)`` Optional. Configuration options for model generation and outputs.
`cachedContent` `string` Optional. The name of the content [cached](https://ai.google.dev/gemini-api/docs/caching) to use as context to serve the prediction. Format: `cachedContents/{cachedContent}`
`serviceTier` ``enum (`https://ai.google.dev/api/generate-content#v1beta.ServiceTier`)`` Optional. The service tier of the request.
`store` `boolean` Optional. Configures the logging behavior for a given request. If set, it takes precedence over the project-level logging config.

### Example request

### Text

### Python

    from google import genai

    client = genai.Client()
    response = client.models.generate_content_stream(
        model="gemini-3.5-flash", contents="Write a story about a magic backpack."
    )
    for chunk in response:
        print(chunk.text)
        print("_" * 80)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    const response = await ai.models.generateContentStream({
      model: "gemini-3.5-flash",
      contents: "Write a story about a magic backpack.",
    });
    let text = "";
    for await (const chunk of response) {
      console.log(chunk.text);
      text += chunk.text;
    }

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }
    contents := []*genai.Content{
    	genai.NewContentFromText("Write a story about a magic backpack.", genai.RoleUser),
    }
    for response, err := range client.Models.GenerateContentStream(
    	ctx,
    	"gemini-3.5-flash",
    	contents,
    	nil,
    ) {
    	if err != nil {
    		log.Fatal(err)
    	}
    	fmt.Print(response.Candidates[0].Content.Parts[0].Text)
    }

### Shell

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=${GEMINI_API_KEY}" \
            -H 'Content-Type: application/json' \
            --no-buffer \
            -d '{ "contents":[{"parts":[{"text": "Write a story about a magic backpack."}]}]}'

### Java

    Client client = new Client();

    ResponseStream<GenerateContentResponse> responseStream =
            client.models.generateContentStream(
                    "gemini-3.5-flash",
                    "Write a story about a magic backpack.",
                    null);

    StringBuilder response = new StringBuilder();
    for (GenerateContentResponse res : responseStream) {
        System.out.print(res.text());
        response.append(res.text());
    }

    responseStream.close();

### Image

### Python

    from google import genai
    import PIL.Image

    client = genai.Client()
    organ = PIL.Image.open(media / "organ.jpg")
    response = client.models.generate_content_stream(
        model="gemini-3.5-flash", contents=["Tell me about this instrument", organ]
    )
    for chunk in response:
        print(chunk.text)
        print("_" * 80)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    const organ = await ai.files.upload({
      file: path.join(media, "organ.jpg"),
    });

    const response = await ai.models.generateContentStream({
      model: "gemini-3.5-flash",
      contents: [
        createUserContent([
          "Tell me about this instrument", 
          createPartFromUri(organ.uri, organ.mimeType)
        ]),
      ],
    });
    let text = "";
    for await (const chunk of response) {
      console.log(chunk.text);
      text += chunk.text;
    }

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }
    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "organ.jpg"), 
    	&genai.UploadFileConfig{
    		MIMEType : "image/jpeg",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }
    parts := []*genai.Part{
    	genai.NewPartFromText("Tell me about this instrument"),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }
    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }
    for response, err := range client.Models.GenerateContentStream(
    	ctx,
    	"gemini-3.5-flash",
    	contents,
    	nil,
    ) {
    	if err != nil {
    		log.Fatal(err)
    	}
    	fmt.Print(response.Candidates[0].Content.Parts[0].Text)
    }

### Shell

    cat > "$TEMP_JSON" << EOF
    {
      "contents": [{
        "parts":[
          {"text": "Tell me about this instrument"},
          {
            "inline_data": {
              "mime_type":"image/jpeg",
              "data": "$(cat "$TEMP_B64")"
            }
          }
        ]
      }]
    }
    EOF

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d "@$TEMP_JSON" 2> /dev/null

### Java

    Client client = new Client();

    String path = media_path + "organ.jpg";
    byte[] imageData = Files.readAllBytes(Paths.get(path));

    Content content =
            Content.fromParts(
                    Part.fromText("Tell me about this instrument."),
                    Part.fromBytes(imageData, "image/jpeg"));


    ResponseStream<GenerateContentResponse> responseStream =
            client.models.generateContentStream(
                    "gemini-3.5-flash",
                    content,
                    null);

    StringBuilder response = new StringBuilder();
    for (GenerateContentResponse res : responseStream) {
        System.out.print(res.text());
        response.append(res.text());
    }

    responseStream.close();

### Audio

### Python

    from google import genai

    client = genai.Client()
    sample_audio = client.files.upload(file=media / "sample.mp3")
    response = client.models.generate_content_stream(
        model="gemini-3.5-flash",
        contents=["Give me a summary of this audio file.", sample_audio],
    )
    for chunk in response:
        print(chunk.text)
        print("_" * 80)

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "sample.mp3"), 
    	&genai.UploadFileConfig{
    		MIMEType : "audio/mpeg",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }

    parts := []*genai.Part{
    	genai.NewPartFromText("Give me a summary of this audio file."),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }

    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }

    for result, err := range client.Models.GenerateContentStream(
    	ctx,
    	"gemini-3.5-flash",
    	contents,
    	nil,
    ) {
    	if err != nil {
    		log.Fatal(err)
    	}
    	fmt.Print(result.Candidates[0].Content.Parts[0].Text)
    }

### Shell

    # Use File API to upload audio data to API request.
    MIME_TYPE=$(file -b --mime-type "${AUDIO_PATH}")
    NUM_BYTES=$(wc -c < "${AUDIO_PATH}")
    DISPLAY_NAME=AUDIO

    tmp_header_file=upload-header.tmp

    # Initial resumable request defining metadata.
    # The upload url is in the response headers dump them to a file.
    curl "${BASE_URL}/upload/v1beta/files?key=${GEMINI_API_KEY}" \
      -D upload-header.tmp \
      -H "X-Goog-Upload-Protocol: resumable" \
      -H "X-Goog-Upload-Command: start" \
      -H "X-Goog-Upload-Header-Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Header-Content-Type: ${MIME_TYPE}" \
      -H "Content-Type: application/json" \
      -d "{'file': {'display_name': '${DISPLAY_NAME}'}}" 2> /dev/null

    upload_url=$(grep -i "x-goog-upload-url: " "${tmp_header_file}" | cut -d" " -f2 | tr -d "\r")
    rm "${tmp_header_file}"

    # Upload the actual bytes.
    curl "${upload_url}" \
      -H "Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Offset: 0" \
      -H "X-Goog-Upload-Command: upload, finalize" \
      --data-binary "@${AUDIO_PATH}" 2> /dev/null > file_info.json

    file_uri=$(jq ".file.uri" file_info.json)
    echo file_uri=$file_uri

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [{
            "parts":[
              {"text": "Please describe this file."},
              {"file_data":{"mime_type": "audio/mpeg", "file_uri": '$file_uri'}}]
            }]
           }' 2> /dev/null > response.json

    cat response.json
    echo

### Video

### Python

    from google import genai
    import time

    client = genai.Client()
    # Video clip (CC BY 3.0) from https://peach.blender.org/download/
    myfile = client.files.upload(file=media / "Big_Buck_Bunny.mp4")
    print(f"{myfile=}")

    # Poll until the video file is completely processed (state becomes ACTIVE).
    while not myfile.state or myfile.state.name != "ACTIVE":
        print("Processing video...")
        print("File state:", myfile.state)
        time.sleep(5)
        myfile = client.files.get(name=myfile.name)

    response = client.models.generate_content_stream(
        model="gemini-3.5-flash", contents=[myfile, "Describe this video clip"]
    )
    for chunk in response:
        print(chunk.text)
        print("_" * 80)

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

    let video = await ai.files.upload({
      file: path.join(media, 'Big_Buck_Bunny.mp4'),
    });

    // Poll until the video file is completely processed (state becomes ACTIVE).
    while (!video.state || video.state.toString() !== 'ACTIVE') {
      console.log('Processing video...');
      console.log('File state: ', video.state);
      await sleep(5000);
      video = await ai.files.get({name: video.name});
    }

    const response = await ai.models.generateContentStream({
      model: "gemini-3.5-flash",
      contents: [
        createUserContent([
          "Describe this video clip",
          createPartFromUri(video.uri, video.mimeType),
        ]),
      ],
    });
    let text = "";
    for await (const chunk of response) {
      console.log(chunk.text);
      text += chunk.text;
    }

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "Big_Buck_Bunny.mp4"), 
    	&genai.UploadFileConfig{
    		MIMEType : "video/mp4",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }

    // Poll until the video file is completely processed (state becomes ACTIVE).
    for file.State == genai.FileStateUnspecified || file.State != genai.FileStateActive {
    	fmt.Println("Processing video...")
    	fmt.Println("File state:", file.State)
    	time.Sleep(5 * time.Second)

    	file, err = client.Files.Get(ctx, file.Name, nil)
    	if err != nil {
    		log.Fatal(err)
    	}
    }

    parts := []*genai.Part{
    	genai.NewPartFromText("Describe this video clip"),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }

    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }

    for result, err := range client.Models.GenerateContentStream(
    	ctx,
    	"gemini-3.5-flash",
    	contents,
    	nil,
    ) {
    	if err != nil {
    		log.Fatal(err)
    	}
    	fmt.Print(result.Candidates[0].Content.Parts[0].Text)
    }

### Shell

    # Use File API to upload audio data to API request.
    MIME_TYPE=$(file -b --mime-type "${VIDEO_PATH}")
    NUM_BYTES=$(wc -c < "${VIDEO_PATH}")
    DISPLAY_NAME=VIDEO_PATH

    # Initial resumable request defining metadata.
    # The upload url is in the response headers dump them to a file.
    curl "${BASE_URL}/upload/v1beta/files?key=${GEMINI_API_KEY}" \
      -D upload-header.tmp \
      -H "X-Goog-Upload-Protocol: resumable" \
      -H "X-Goog-Upload-Command: start" \
      -H "X-Goog-Upload-Header-Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Header-Content-Type: ${MIME_TYPE}" \
      -H "Content-Type: application/json" \
      -d "{'file': {'display_name': '${DISPLAY_NAME}'}}" 2> /dev/null

    upload_url=$(grep -i "x-goog-upload-url: " "${tmp_header_file}" | cut -d" " -f2 | tr -d "\r")
    rm "${tmp_header_file}"

    # Upload the actual bytes.
    curl "${upload_url}" \
      -H "Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Offset: 0" \
      -H "X-Goog-Upload-Command: upload, finalize" \
      --data-binary "@${VIDEO_PATH}" 2> /dev/null > file_info.json

    file_uri=$(jq ".file.uri" file_info.json)
    echo file_uri=$file_uri

    state=$(jq ".file.state" file_info.json)
    echo state=$state

    while [[ "($state)" = *"PROCESSING"* ]];
    do
      echo "Processing video..."
      sleep 5
      # Get the file of interest to check state
      curl https://generativelanguage.googleapis.com/v1beta/files/$name > file_info.json
      state=$(jq ".file.state" file_info.json)
    done

    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [{
            "parts":[
              {"text": "Please describe this file."},
              {"file_data":{"mime_type": "video/mp4", "file_uri": '$file_uri'}}]
            }]
           }' 2> /dev/null > response.json

    cat response.json
    echo

### PDF

### Python

    from google import genai

    client = genai.Client()
    sample_pdf = client.files.upload(file=media / "test.pdf")
    response = client.models.generate_content_stream(
        model="gemini-3.5-flash",
        contents=["Give me a summary of this document:", sample_pdf],
    )

    for chunk in response:
        print(chunk.text)
        print("_" * 80)

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    file, err := client.Files.UploadFromPath(
    	ctx, 
    	filepath.Join(getMedia(), "test.pdf"), 
    	&genai.UploadFileConfig{
    		MIMEType : "application/pdf",
    	},
    )
    if err != nil {
    	log.Fatal(err)
    }

    parts := []*genai.Part{
    	genai.NewPartFromText("Give me a summary of this document:"),
    	genai.NewPartFromURI(file.URI, file.MIMEType),
    }

    contents := []*genai.Content{
    	genai.NewContentFromParts(parts, genai.RoleUser),
    }

    for result, err := range client.Models.GenerateContentStream(
    	ctx,
    	"gemini-3.5-flash",
    	contents,
    	nil,
    ) {
    	if err != nil {
    		log.Fatal(err)
    	}
    	fmt.Print(result.Candidates[0].Content.Parts[0].Text)
    }

### Shell

    MIME_TYPE=$(file -b --mime-type "${PDF_PATH}")
    NUM_BYTES=$(wc -c < "${PDF_PATH}")
    DISPLAY_NAME=TEXT


    echo $MIME_TYPE
    tmp_header_file=upload-header.tmp

    # Initial resumable request defining metadata.
    # The upload url is in the response headers dump them to a file.
    curl "${BASE_URL}/upload/v1beta/files?key=${GEMINI_API_KEY}" \
      -D upload-header.tmp \
      -H "X-Goog-Upload-Protocol: resumable" \
      -H "X-Goog-Upload-Command: start" \
      -H "X-Goog-Upload-Header-Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Header-Content-Type: ${MIME_TYPE}" \
      -H "Content-Type: application/json" \
      -d "{'file': {'display_name': '${DISPLAY_NAME}'}}" 2> /dev/null

    upload_url=$(grep -i "x-goog-upload-url: " "${tmp_header_file}" | cut -d" " -f2 | tr -d "\r")
    rm "${tmp_header_file}"

    # Upload the actual bytes.
    curl "${upload_url}" \
      -H "Content-Length: ${NUM_BYTES}" \
      -H "X-Goog-Upload-Offset: 0" \
      -H "X-Goog-Upload-Command: upload, finalize" \
      --data-binary "@${PDF_PATH}" 2> /dev/null > file_info.json

    file_uri=$(jq ".file.uri" file_info.json)
    echo file_uri=$file_uri

    # Now generate content using that file
    curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=$GEMINI_API_KEY" \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [{
            "parts":[
              {"text": "Can you add a few more lines to this poem?"},
              {"file_data":{"mime_type": "application/pdf", "file_uri": '$file_uri'}}]
            }]
           }' 2> /dev/null > response.json

    cat response.json
    echo

### Chat

### Python

    from google import genai
    from google.genai import types

    client = genai.Client()
    chat = client.chats.create(
        model="gemini-3.5-flash",
        history=[
            types.Content(role="user", parts=[types.Part(text="Hello")]),
            types.Content(
                role="model",
                parts=[
                    types.Part(
                        text="Great to meet you. What would you like to know?"
                    )
                ],
            ),
        ],
    )
    response = chat.send_message_stream(message="I have 2 dogs in my house.")
    for chunk in response:
        print(chunk.text)
        print("_" * 80)
    response = chat.send_message_stream(message="How many paws are in my house?")
    for chunk in response:
        print(chunk.text)
        print("_" * 80)

    print(chat.get_history())

### Node.js

    // Make sure to include the following import:
    // import {GoogleGenAI} from '@google/genai';
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
    const chat = ai.chats.create({
      model: "gemini-3.5-flash",
      history: [
        {
          role: "user",
          parts: [{ text: "Hello" }],
        },
        {
          role: "model",
          parts: [{ text: "Great to meet you. What would you like to know?" }],
        },
      ],
    });

    console.log("Streaming response for first message:");
    const stream1 = await chat.sendMessageStream({
      message: "I have 2 dogs in my house.",
    });
    for await (const chunk of stream1) {
      console.log(chunk.text);
      console.log("_".repeat(80));
    }

    console.log("Streaming response for second message:");
    const stream2 = await chat.sendMessageStream({
      message: "How many paws are in my house?",
    });
    for await (const chunk of stream2) {
      console.log(chunk.text);
      console.log("_".repeat(80));
    }

    console.log(chat.getHistory());

### Go

    ctx := context.Background()
    client, err := genai.NewClient(ctx, &genai.ClientConfig{
    	APIKey:  os.Getenv("GEMINI_API_KEY"),
    	Backend: genai.BackendGeminiAPI,
    })
    if err != nil {
    	log.Fatal(err)
    }

    history := []*genai.Content{
    	genai.NewContentFromText("Hello", genai.RoleUser),
    	genai.NewContentFromText("Great to meet you. What would you like to know?", genai.RoleModel),
    }
    chat, err := client.Chats.Create(ctx, "gemini-3.5-flash", nil, history)
    if err != nil {
    	log.Fatal(err)
    }

    for chunk, err := range chat.SendMessageStream(ctx, genai.Part{Text: "I have 2 dogs in my house."}) {
    	if err != nil {
    		log.Fatal(err)
    	}
    	fmt.Println(chunk.Text())
    	fmt.Println(strings.Repeat("_", 64))
    }

    for chunk, err := range chat.SendMessageStream(ctx, genai.Part{Text: "How many paws are in my house?"}) {
    	if err != nil {
    		log.Fatal(err)
    	}
    	fmt.Println(chunk.Text())
    	fmt.Println(strings.Repeat("_", 64))
    }

    fmt.Println(chat.History(false))

### Shell

    curl https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=$GEMINI_API_KEY \
        -H 'Content-Type: application/json' \
        -X POST \
        -d '{
          "contents": [
            {"role":"user",
             "parts":[{
               "text": "Hello"}]},
            {"role": "model",
             "parts":[{
               "text": "Great to meet you. What would you like to know?"}]},
            {"role":"user",
             "parts":[{
               "text": "I have two dogs in my house. How many paws are in my house?"}]},
          ]
        }' 2> /dev/null | grep "text"

### Response body

If successful, the response body contains a stream of `https://ai.google.dev/api/generate-content#v1beta.GenerateContentResponse` instances.

## GenerateContentResponse

- [JSON representation](https://ai.google.dev/api/generate-content#SCHEMA_REPRESENTATION)
- [PromptFeedback](https://ai.google.dev/api/generate-content#PromptFeedback)
  - [JSON representation](https://ai.google.dev/api/generate-content#PromptFeedback.SCHEMA_REPRESENTATION)
- [BlockReason](https://ai.google.dev/api/generate-content#BlockReason)
- [UsageMetadata](https://ai.google.dev/api/generate-content#UsageMetadata)
  - [JSON representation](https://ai.google.dev/api/generate-content#UsageMetadata.SCHEMA_REPRESENTATION)
- [ModelStatus](https://ai.google.dev/api/generate-content#ModelStatus)
  - [JSON representation](https://ai.google.dev/api/generate-content#ModelStatus.SCHEMA_REPRESENTATION)
- [ModelStage](https://ai.google.dev/api/generate-content#ModelStage)

Response from the model supporting multiple candidate responses.

Safety ratings and content filtering are reported for both prompt in `GenerateContentResponse.prompt_feedback` and for each candidate in `finishReason` and in `safetyRatings`. The API: - Returns either all requested candidates or none of them - Returns no candidates at all only if there was something wrong with the prompt (check `promptFeedback`) - Reports feedback on each candidate in `finishReason` and `safetyRatings`.
Fields `candidates[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.Candidate`)`` Candidate responses from the model.
`promptFeedback` ``object (`https://ai.google.dev/api/generate-content#PromptFeedback`)`` Returns the prompt's feedback related to the content filters.
`usageMetadata` ``object (`https://ai.google.dev/api/generate-content#UsageMetadata`)`` Output only. Metadata on the generation requests' token usage.
`modelVersion` `string` Output only. The model version used to generate the response.
`responseId` `string` Output only. responseId is used to identify each response.
`modelStatus` ``object (`https://ai.google.dev/api/generate-content#ModelStatus`)`` Output only. The current model status of this model.

| JSON representation |
|---|
| ``` { "candidates": [ { object (`https://ai.google.dev/api/generate-content#v1beta.Candidate`) } ], "promptFeedback": { object (`https://ai.google.dev/api/generate-content#PromptFeedback`) }, "usageMetadata": { object (`https://ai.google.dev/api/generate-content#UsageMetadata`) }, "modelVersion": string, "responseId": string, "modelStatus": { object (`https://ai.google.dev/api/generate-content#ModelStatus`) } } ``` |

## PromptFeedback

A set of the feedback metadata the prompt specified in `GenerateContentRequest.content`.
Fields `blockReason` ``enum (`https://ai.google.dev/api/generate-content#BlockReason`)`` Optional. If set, the prompt was blocked and no candidates are returned. Rephrase the prompt.
`safetyRatings[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.SafetyRating`)`` Ratings for safety of the prompt. There is at most one rating per category.

| JSON representation |
|---|
| ``` { "blockReason": enum (`https://ai.google.dev/api/generate-content#BlockReason`), "safetyRatings": [ { object (`https://ai.google.dev/api/generate-content#v1beta.SafetyRating`) } ] } ``` |

## BlockReason

Specifies the reason why the prompt was blocked.

| Enums ||
|---|---|
| `BLOCK_REASON_UNSPECIFIED` | Default value. This value is unused. |
| `SAFETY` | Prompt was blocked due to safety reasons. Inspect `safetyRatings` to understand which safety category blocked it. |
| `OTHER` | Prompt was blocked due to unknown reasons. |
| `BLOCKLIST` | Prompt was blocked due to the terms which are included from the terminology blocklist. |
| `PROHIBITED_CONTENT` | Prompt was blocked due to prohibited content. |
| `IMAGE_SAFETY` | Candidates blocked due to unsafe image generation content. |

## UsageMetadata

Metadata on the generation request's token usage.
Fields `promptTokenCount` `integer` Number of tokens in the prompt. When `cachedContent` is set, this is still the total effective prompt size meaning this includes the number of tokens in the cached content.
`cachedContentTokenCount` `integer` Number of tokens in the cached part of the prompt (the cached content)
`candidatesTokenCount` `integer` Total number of tokens across all the generated response candidates.
`toolUsePromptTokenCount` `integer` Output only. Number of tokens present in tool-use prompt(s).
`thoughtsTokenCount` `integer` Output only. Number of tokens of thoughts for thinking models.
`totalTokenCount` `integer` Total token count for the generation request (prompt + thoughts + response candidates).
`promptTokensDetails[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`)`` Output only. List of modalities that were processed in the request input.
`cacheTokensDetails[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`)`` Output only. List of modalities of the cached content in the request input.
`candidatesTokensDetails[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`)`` Output only. List of modalities that were returned in the response.
`toolUsePromptTokensDetails[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`)`` Output only. List of modalities that were processed for tool-use request inputs.
`serviceTier` ``enum (`https://ai.google.dev/api/generate-content#v1beta.ServiceTier`)`` Output only. Service tier of the request.

| JSON representation |
|---|
| ``` { "promptTokenCount": integer, "cachedContentTokenCount": integer, "candidatesTokenCount": integer, "toolUsePromptTokenCount": integer, "thoughtsTokenCount": integer, "totalTokenCount": integer, "promptTokensDetails": [ { object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`) } ], "cacheTokensDetails": [ { object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`) } ], "candidatesTokensDetails": [ { object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`) } ], "toolUsePromptTokensDetails": [ { object (`https://ai.google.dev/api/generate-content#v1beta.ModalityTokenCount`) } ], "serviceTier": enum (`https://ai.google.dev/api/generate-content#v1beta.ServiceTier`) } ``` |

## ModelStatus

The status of the underlying model. This is used to indicate the stage of the underlying model and the retirement time if applicable.
Fields `modelStage` ``enum (`https://ai.google.dev/api/generate-content#ModelStage`)`` The stage of the underlying model.
`retirementTime` ``string (`https://protobuf.dev/reference/protobuf/google.protobuf#timestamp` format)`` The time at which the model will be retired.

Uses RFC 3339, where generated output will always be Z-normalized and use 0, 3, 6 or 9 fractional digits. Offsets other than "Z" are also accepted. Examples: `"2014-10-02T15:01:23Z"`, `"2014-10-02T15:01:23.045123456Z"` or `"2014-10-02T15:01:23+05:30"`.
`message` `string` A message explaining the model status.

| JSON representation |
|---|
| ``` { "modelStage": enum (`https://ai.google.dev/api/generate-content#ModelStage`), "retirementTime": string, "message": string } ``` |

## ModelStage

Defines the stage of the underlying model.

| Enums ||
|---|---|
| `MODEL_STAGE_UNSPECIFIED` | Unspecified model stage. |
| `UNSTABLE_EXPERIMENTAL` | The underlying model is subject to lots of tunings. > [!WARNING] > This item is deprecated! |
| `EXPERIMENTAL` | Models in this stage are for experimental purposes only. |
| `PREVIEW` | Models in this stage are more mature than experimental models. |
| `STABLE` | Models in this stage are considered stable and ready for production use. |
| `LEGACY` | If the model is on this stage, it means that this model is on the path to deprecation in near future. Only existing customers can use this model. |
| `DEPRECATED` | Models in this stage are deprecated. These models cannot be used. > [!WARNING] > This item is deprecated! |
| `RETIRED` | Models in this stage are retired. These models cannot be used. |

## Candidate

- [JSON representation](https://ai.google.dev/api/generate-content#SCHEMA_REPRESENTATION)
- [FinishReason](https://ai.google.dev/api/generate-content#FinishReason)
- [GroundingAttribution](https://ai.google.dev/api/generate-content#GroundingAttribution)
  - [JSON representation](https://ai.google.dev/api/generate-content#GroundingAttribution.SCHEMA_REPRESENTATION)
- [AttributionSourceId](https://ai.google.dev/api/generate-content#AttributionSourceId)
  - [JSON representation](https://ai.google.dev/api/generate-content#AttributionSourceId.SCHEMA_REPRESENTATION)
- [GroundingPassageId](https://ai.google.dev/api/generate-content#GroundingPassageId)
  - [JSON representation](https://ai.google.dev/api/generate-content#GroundingPassageId.SCHEMA_REPRESENTATION)
- [SemanticRetrieverChunk](https://ai.google.dev/api/generate-content#SemanticRetrieverChunk)
  - [JSON representation](https://ai.google.dev/api/generate-content#SemanticRetrieverChunk.SCHEMA_REPRESENTATION)
- [GroundingMetadata](https://ai.google.dev/api/generate-content#GroundingMetadata)
  - [JSON representation](https://ai.google.dev/api/generate-content#GroundingMetadata.SCHEMA_REPRESENTATION)
- [SearchEntryPoint](https://ai.google.dev/api/generate-content#SearchEntryPoint)
  - [JSON representation](https://ai.google.dev/api/generate-content#SearchEntryPoint.SCHEMA_REPRESENTATION)
- [GroundingChunk](https://ai.google.dev/api/generate-content#GroundingChunk)
  - [JSON representation](https://ai.google.dev/api/generate-content#GroundingChunk.SCHEMA_REPRESENTATION)
- [Web](https://ai.google.dev/api/generate-content#Web)
  - [JSON representation](https://ai.google.dev/api/generate-content#Web.SCHEMA_REPRESENTATION)
- [Image](https://ai.google.dev/api/generate-content#Image)
  - [JSON representation](https://ai.google.dev/api/generate-content#Image.SCHEMA_REPRESENTATION)
- [RetrievedContext](https://ai.google.dev/api/generate-content#RetrievedContext)
  - [JSON representation](https://ai.google.dev/api/generate-content#RetrievedContext.SCHEMA_REPRESENTATION)
- [CustomMetadata](https://ai.google.dev/api/generate-content#CustomMetadata)
  - [JSON representation](https://ai.google.dev/api/generate-content#CustomMetadata.SCHEMA_REPRESENTATION)
- [StringList](https://ai.google.dev/api/generate-content#StringList)
  - [JSON representation](https://ai.google.dev/api/generate-content#StringList.SCHEMA_REPRESENTATION)
- [Maps](https://ai.google.dev/api/generate-content#Maps)
  - [JSON representation](https://ai.google.dev/api/generate-content#Maps.SCHEMA_REPRESENTATION)
- [PlaceAnswerSources](https://ai.google.dev/api/generate-content#PlaceAnswerSources)
  - [JSON representation](https://ai.google.dev/api/generate-content#PlaceAnswerSources.SCHEMA_REPRESENTATION)
- [ReviewSnippet](https://ai.google.dev/api/generate-content#ReviewSnippet)
  - [JSON representation](https://ai.google.dev/api/generate-content#ReviewSnippet.SCHEMA_REPRESENTATION)
- [GroundingSupport](https://ai.google.dev/api/generate-content#GroundingSupport)
  - [JSON representation](https://ai.google.dev/api/generate-content#GroundingSupport.SCHEMA_REPRESENTATION)
- [Segment](https://ai.google.dev/api/generate-content#Segment)
  - [JSON representation](https://ai.google.dev/api/generate-content#Segment.SCHEMA_REPRESENTATION)
- [RetrievalMetadata](https://ai.google.dev/api/generate-content#RetrievalMetadata)
  - [JSON representation](https://ai.google.dev/api/generate-content#RetrievalMetadata.SCHEMA_REPRESENTATION)
- [LogprobsResult](https://ai.google.dev/api/generate-content#LogprobsResult)
  - [JSON representation](https://ai.google.dev/api/generate-content#LogprobsResult.SCHEMA_REPRESENTATION)
- [TopCandidates](https://ai.google.dev/api/generate-content#TopCandidates)
  - [JSON representation](https://ai.google.dev/api/generate-content#TopCandidates.SCHEMA_REPRESENTATION)
- [Candidate](https://ai.google.dev/api/generate-content#Candidate)
  - [JSON representation](https://ai.google.dev/api/generate-content#Candidate.SCHEMA_REPRESENTATION)
- [UrlContextMetadata](https://ai.google.dev/api/generate-content#UrlContextMetadata)
  - [JSON representation](https://ai.google.dev/api/generate-content#UrlContextMetadata.SCHEMA_REPRESENTATION)
- [UrlMetadata](https://ai.google.dev/api/generate-content#UrlMetadata)
  - [JSON representation](https://ai.google.dev/api/generate-content#UrlMetadata.SCHEMA_REPRESENTATION)
- [UrlRetrievalStatus](https://ai.google.dev/api/generate-content#UrlRetrievalStatus)

A response candidate generated from the model.
Fields `content` ``object (`https://ai.google.dev/api/caching#Content`)`` Output only. Generated content returned from the model.
`finishReason` ``enum (`https://ai.google.dev/api/generate-content#FinishReason`)`` Optional. Output only. The reason why the model stopped generating tokens.

If empty, the model has not stopped generating tokens.
`safetyRatings[]` ``object (`https://ai.google.dev/api/generate-content#v1beta.SafetyRating`)`` List of ratings for the safety of a response candidate.

There is at most one rating per category.
`citationMetadata` ``object (`https://ai.google.dev/api/generate-content#v1beta.CitationMetadata`)`` Output only. Citation information for model-generated candidate.

This field may be populated with recitation information for any text included in the `content`. These are passages that are "recited" from copyrighted material in the foundational LLM's training data.
`tokenCount` `integer` Output only. Token count for this candidate.
`groundingAttributions[]` ``object (`https://ai.google.dev/api/generate-content#GroundingAttribution`)`` Output only. Attribution information for sources that contributed to a grounded answer.

This field is populated for `GenerateAnswer` calls.
`groundingMetadata` ``object (`https://ai.google.dev/api/generate-content#GroundingMetadata`)`` Output only. Grounding metadata for the candidate.

This field is populated for `GenerateContent` calls.
`avgLogprobs` `number` Output only. Average log probability score of the candidate.
`logprobsResult` ``object (`https://ai.google.dev/api/generate-content#LogprobsResult`)`` Output only. Log-likelihood scores for the response tokens and top tokens
`urlContextMetadata` ``object (`https://ai.google.dev/api/generate-content#UrlContextMetadata`)`` Output only. Metadata related to url context retrieval tool.
`index` `integer` Output only. Index of the candidate in the list of response candidates.
`finishMessage` `string` Optional. Output only. Details the reason why the model stopped generating tokens. This is populated only when `finishReason` is set.

| JSON representation |
|---|
| ``` { "content": { object (`https://ai.google.dev/api/caching#Content`) }, "finishReason": enum (`https://ai.google.dev/api/generate-content#FinishReason`), "safetyRatings": [ { object (`https://ai.google.dev/api/generate-content#v1beta.SafetyRating`) } ], "citationMetadata": { object (`https://ai.google.dev/api/generate-content#v1beta.CitationMetadata`) }, "tokenCount": integer, "groundingAttributions": [ { object (`https://ai.google.dev/api/generate-content#GroundingAttribution`) } ], "groundingMetadata": { object (`https://ai.google.dev/api/generate-content#GroundingMetadata`) }, "avgLogprobs": number, "logprobsResult": { object (`https://ai.google.dev/api/generate-content#LogprobsResult`) }, "urlContextMetadata": { object (`https://ai.google.dev/api/generate-content#UrlContextMetadata`) }, "index": integer, "finishMessage": string } ``` |

## FinishReason

Defines the reason why the model stopped generating tokens.

| Enums ||
|---|---|
| `FINISH_REASON_UNSPECIFIED` | Default value. This value is unused. |
| `STOP` | Natural stop point of the model or provided stop sequence. |
| `MAX_TOKENS` | The maximum number of tokens as specified in the request was reached. |
| `SAFETY` | The response candidate content was flagged for safety reasons. |
| `RECITATION` | The response candidate content was flagged for recitation reasons. |
| `LANGUAGE` | The response candidate content was flagged for using an unsupported language. |
| `OTHER` | Unknown reason. |
| `BLOCKLIST` | Token generation stopped because the content contains forbidden terms. |
| `PROHIBITED_CONTENT` | Token generation stopped for potentially containing prohibited content. |
| `SPII` | Token generation stopped because the content potentially contains Sensitive Personally Identifiable Information (SPII). |
| `MALFORMED_FUNCTION_CALL` | The function call generated by the model is invalid. |
| `IMAGE_SAFETY` | Token generation stopped because generated images contain safety violations. |
| `IMAGE_PROHIBITED_CONTENT` | Image generation stopped because generated images has other prohibited content. |
| `IMAGE_OTHER` | Image generation stopped because of other miscellaneous issue. |
| `NO_IMAGE` | The model was expected to generate an image, but none was generated. |
| `IMAGE_RECITATION` | Image generation stopped due to recitation. |
| `UNEXPECTED_TOOL_CALL` | Model generated a tool call but no tools were enabled in the request. |
| `TOO_MANY_TOOL_CALLS` | Model called too many tools consecutively, thus the system exited execution. |
| `MISSING_THOUGHT_SIGNATURE` | Request has at least one thought signature missing. |
| `MALFORMED_RESPONSE` | Finished due to malformed response. |

## GroundingAttribution

Attribution for a source that contributed to an answer.
Fields `sourceId` ``object (`https://ai.google.dev/api/generate-content#AttributionSourceId`)`` Output only. Identifier for the source contributing to this attribution.
`content` ``object (`https://ai.google.dev/api/caching#Content`)`` Grounding source content that makes up this attribution.

| JSON representation |
|---|
| ``` { "sourceId": { object (`https://ai.google.dev/api/generate-content#AttributionSourceId`) }, "content": { object (`https://ai.google.dev/api/caching#Content`) } } ``` |

## AttributionSourceId

Identifier for the source contributing to this attribution.
Fields `source` `Union type` `source` can be only one of the following: `groundingPassage` ``object (`https://ai.google.dev/api/generate-content#GroundingPassageId`)`` Identifier for an inline passage.
`semanticRetrieverChunk` ``object (`https://ai.google.dev/api/generate-content#SemanticRetrieverChunk`)`` Identifier for a `Chunk` fetched via Semantic Retriever.

| JSON representation |
|---|
| ``` { // source "groundingPassage": { object (`https://ai.google.dev/api/generate-content#GroundingPassageId`) }, "semanticRetrieverChunk": { object (`https://ai.google.dev/api/generate-content#SemanticRetrieverChunk`) } // Union type } ``` |

## GroundingPassageId

Identifier for a part within a `GroundingPassage`.
Fields `passageId` `string` Output only. ID of the passage matching the `GenerateAnswerRequest`'s `GroundingPassage.id`.
`partIndex` `integer` Output only. Index of the part within the `GenerateAnswerRequest`'s `GroundingPassage.content`.

| JSON representation |
|---|
| ``` { "passageId": string, "partIndex": integer } ``` |

## SemanticRetrieverChunk

Identifier for a `Chunk` retrieved via Semantic Retriever specified in the `GenerateAnswerRequest` using `SemanticRetrieverConfig`.
Fields `source` `string` Output only. Name of the source matching the request's `SemanticRetrieverConfig.source`. Example: `corpora/123` or `corpora/123/documents/abc`
`chunk` `string` Output only. Name of the `Chunk` containing the attributed text. Example: `corpora/123/documents/abc/chunks/xyz`

| JSON representation |
|---|
| ``` { "source": string, "chunk": string } ``` |

## GroundingMetadata

Metadata returned to client when grounding is enabled.
Fields `groundingChunks[]` ``object (`https://ai.google.dev/api/generate-content#GroundingChunk`)`` List of supporting references retrieved from specified grounding source. When streaming, this only contains the grounding chunks that have not been included in the grounding metadata of previous responses.
`groundingSupports[]` ``object (`https://ai.google.dev/api/generate-content#GroundingSupport`)`` List of grounding support.
`webSearchQueries[]` `string` Web search queries for the following-up web search.
`imageSearchQueries[]` `string` Image search queries used for grounding.
`searchEntryPoint` ``object (`https://ai.google.dev/api/generate-content#SearchEntryPoint`)`` Optional. Google search entry for the following-up web searches.
`retrievalMetadata` ``object (`https://ai.google.dev/api/generate-content#RetrievalMetadata`)`` Metadata related to retrieval in the grounding flow.
`googleMapsWidgetContextToken` `string` Optional. Resource name of the Google Maps widget context token that can be used with the PlacesContextElement widget in order to render contextual data. Only populated in the case that grounding with Google Maps is enabled.

| JSON representation |
|---|
| ``` { "groundingChunks": [ { object (`https://ai.google.dev/api/generate-content#GroundingChunk`) } ], "groundingSupports": [ { object (`https://ai.google.dev/api/generate-content#GroundingSupport`) } ], "webSearchQueries": [ string ], "imageSearchQueries": [ string ], "searchEntryPoint": { object (`https://ai.google.dev/api/generate-content#SearchEntryPoint`) }, "retrievalMetadata": { object (`https://ai.google.dev/api/generate-content#RetrievalMetadata`) }, "googleMapsWidgetContextToken": string } ``` |

## SearchEntryPoint

Google search entry point.
Fields `renderedContent` `string` Optional. Web content snippet that can be embedded in a web page or an app webview.
`sdkBlob` `string (https://developers.google.com/discovery/v1/type-format format)` Optional. Base64 encoded JSON representing array of \<search term, search url\> tuple.

A base64-encoded string.

| JSON representation |
|---|
| ``` { "renderedContent": string, "sdkBlob": string } ``` |

## GroundingChunk

A `GroundingChunk` represents a segment of supporting evidence that grounds the model's response. It can be a chunk from the web, a retrieved context from a file, or information from Google Maps.
Fields `chunk_type` `Union type` Chunk type. `chunk_type` can be only one of the following: `web` ``object (`https://ai.google.dev/api/generate-content#Web`)`` Grounding chunk from the web.
`image` ``object (`https://ai.google.dev/api/generate-content#Image`)`` Optional. Grounding chunk from image search.
`retrievedContext` ``object (`https://ai.google.dev/api/generate-content#RetrievedContext`)`` Optional. Grounding chunk from context retrieved by the file search tool.
`maps` ``object (`https://ai.google.dev/api/generate-content#Maps`)`` Optional. Grounding chunk from Google Maps.

| JSON representation |
|---|
| ``` { // chunk_type "web": { object (`https://ai.google.dev/api/generate-content#Web`) }, "image": { object (`https://ai.google.dev/api/generate-content#Image`) }, "retrievedContext": { object (`https://ai.google.dev/api/generate-content#RetrievedContext`) }, "maps": { object (`https://ai.google.dev/api/generate-content#Maps`) } // Union type } ``` |

## Web

Chunk from the web.
Fields `uri` `string` Output only. URI reference of the chunk.
`title` `string` Output only. Title of the chunk.

| JSON representation |
|---|
| ``` { "uri": string, "title": string } ``` |

## Image

Chunk from image search.
Fields `sourceUri` `string` The web page URI for attribution.
`imageUri` `string` The image asset URL.
`title` `string` The title of the web page that the image is from.
`domain` `string` The root domain of the web page that the image is from, e.g. "example.com".

| JSON representation |
|---|
| ``` { "sourceUri": string, "imageUri": string, "title": string, "domain": string } ``` |

## RetrievedContext

Chunk from context retrieved by the file search tool.
Fields `customMetadata[]` ``object (`https://ai.google.dev/api/generate-content#CustomMetadata`)`` Optional. User-provided metadata about the retrieved context.
`uri` `string` Optional. URI reference of the semantic retrieval document.
`title` `string` Optional. Title of the document.
`text` `string` Optional. Text of the chunk.
`fileSearchStore` `string` Optional. Name of the `FileSearchStore` containing the document. Example: `fileSearchStores/123`
`pageNumber` `integer` Optional. Page number of the retrieved context, if applicable.
`mediaId` `string` Optional. The media blob resource name for multimodal file search results. Format: fileSearchStores/{file_search_store_id}/media/{blobId}

| JSON representation |
|---|
| ``` { "customMetadata": [ { object (`https://ai.google.dev/api/generate-content#CustomMetadata`) } ], "uri": string, "title": string, "text": string, "fileSearchStore": string, "pageNumber": integer, "mediaId": string } ``` |

## CustomMetadata

User provided metadata about the GroundingFact.
Fields `key` `string` The key of the metadata.
`value` `Union type` The value of the metadata. Can be a string, a list of strings, or a number. `value` can be only one of the following: `stringValue` `string` Optional. The string value of the metadata.
`stringListValue` ``object (`https://ai.google.dev/api/generate-content#StringList`)`` Optional. A list of string values for the metadata.
`numericValue` `number` Optional. The numeric value of the metadata. The expected range for this value depends on the specific `key` used.

| JSON representation |
|---|
| ``` { "key": string, // value "stringValue": string, "stringListValue": { object (`https://ai.google.dev/api/generate-content#StringList`) }, "numericValue": number // Union type } ``` |

## StringList

A list of string values.
Fields `values[]` `string` The string values of the list.

| JSON representation |
|---|
| ``` { "values": [ string ] } ``` |

## Maps

A grounding chunk from Google Maps. A Maps chunk corresponds to a single place.
Fields `uri` `string` URI reference of the place.
`title` `string` Title of the place.
`text` `string` Text description of the place answer.
`placeId` `string` The ID of the place, in `places/{placeId}` format. A user can use this ID to look up that place.
`placeAnswerSources` ``object (`https://ai.google.dev/api/generate-content#PlaceAnswerSources`)`` Sources that provide answers about the features of a given place in Google Maps.

| JSON representation |
|---|
| ``` { "uri": string, "title": string, "text": string, "placeId": string, "placeAnswerSources": { object (`https://ai.google.dev/api/generate-content#PlaceAnswerSources`) } } ``` |

## PlaceAnswerSources

Collection of sources that provide answers about the features of a given place in Google Maps. Each PlaceAnswerSources message corresponds to a specific place in Google Maps. The Google Maps tool used these sources in order to answer questions about features of the place (e.g: "does Bar Foo have Wifi" or "is Foo Bar wheelchair accessible?"). Currently we only support review snippets as sources.
Fields `reviewSnippets[]` ``object (`https://ai.google.dev/api/generate-content#ReviewSnippet`)`` Snippets of reviews that are used to generate answers about the features of a given place in Google Maps.

| JSON representation |
|---|
| ``` { "reviewSnippets": [ { object (`https://ai.google.dev/api/generate-content#ReviewSnippet`) } ] } ``` |

## ReviewSnippet

Encapsulates a snippet of a user review that answers a question about the features of a specific place in Google Maps.
Fields `reviewId` `string` The ID of the review snippet.
`googleMapsUri` `string` A link that corresponds to the user review on Google Maps.
`title` `string` Title of the review.

| JSON representation |
|---|
| ``` { "reviewId": string, "googleMapsUri": string, "title": string } ``` |

## GroundingSupport

Grounding support.
Fields `groundingChunkIndices[]` `integer` Optional. A list of indices (into 'grounding_chunk' in `response.candidate.grounding_metadata`) specifying the citations associated with the claim. For instance \[1,3,4\] means that grounding_chunk\[1\], grounding_chunk\[3\], grounding_chunk\[4\] are the retrieved content attributed to the claim. If the response is streaming, the groundingChunkIndices refer to the indices across all responses. It is the client's responsibility to accumulate the grounding chunks from all responses (while maintaining the same order).
`confidenceScores[]` `number` Optional. Confidence score of the support references. Ranges from 0 to 1. 1 is the most confident. This list must have the same size as the groundingChunkIndices.
`renderedParts[]` `integer` Output only. Indices into the `parts` field of the candidate's content. These indices specify which rendered parts are associated with this support source.
`segment` ``object (`https://ai.google.dev/api/generate-content#Segment`)`` Segment of the content this support belongs to.

| JSON representation |
|---|
| ``` { "groundingChunkIndices": [ integer ], "confidenceScores": [ number ], "renderedParts": [ integer ], "segment": { object (`https://ai.google.dev/api/generate-content#Segment`) } } ``` |

## Segment

Segment of the content.
Fields `partIndex` `integer` The index of a Part object within its parent Content object.
`startIndex` `integer` Start index in the given Part, measured in bytes. Offset from the start of the Part, inclusive, starting at zero.
`endIndex` `integer` End index in the given Part, measured in bytes. Offset from the start of the Part, exclusive, starting at zero.
`text` `string` The text corresponding to the segment from the response.

| JSON representation |
|---|
| ``` { "partIndex": integer, "startIndex": integer, "endIndex": integer, "text": string } ``` |

## RetrievalMetadata

Metadata related to retrieval in the grounding flow.
Fields `googleSearchDynamicRetrievalScore` `number` Optional. Score indicating how likely information from google search could help answer the prompt. The score is in the range \[0, 1\], where 0 is the least likely and 1 is the most likely. This score is only populated when google search grounding and dynamic retrieval is enabled. It will be compared to the threshold to determine whether to trigger google search.

| JSON representation |
|---|
| ``` { "googleSearchDynamicRetrievalScore": number } ``` |

## LogprobsResult

Logprobs Result
Fields `topCandidates[]` ``object (`https://ai.google.dev/api/generate-content#TopCandidates`)`` Length = total number of decoding steps.
`chosenCandidates[]` ``object (`https://ai.google.dev/api/generate-content#Candidate`)`` Length = total number of decoding steps. The chosen candidates may or may not be in topCandidates.
`logProbabilitySum` `number` Sum of log probabilities for all tokens.

| JSON representation |
|---|
| ``` { "topCandidates": [ { object (`https://ai.google.dev/api/generate-content#TopCandidates`) } ], "chosenCandidates": [ { object (`https://ai.google.dev/api/generate-content#Candidate`) } ], "logProbabilitySum": number } ``` |

## TopCandidates

Candidates with top log probabilities at each decoding step.
Fields `candidates[]` ``object (`https://ai.google.dev/api/generate-content#Candidate`)`` Sorted by log probability in descending order.

| JSON representation |
|---|
| ``` { "candidates": [ { object (`https://ai.google.dev/api/generate-content#Candidate`) } ] } ``` |

## Candidate

Candidate for the logprobs token and score.
Fields `token` `string` The candidate's token string value.
`tokenId` `integer` The candidate's token id value.
`logProbability` `number` The candidate's log probability.

| JSON representation |
|---|
| ``` { "token": string, "tokenId": integer, "logProbability": number } ``` |

## UrlContextMetadata

Metadata related to url context retrieval tool.
Fields `urlMetadata[]` ``object (`https://ai.google.dev/api/generate-content#UrlMetadata`)`` List of url context.

| JSON representation |
|---|
| ``` { "urlMetadata": [ { object (`https://ai.google.dev/api/generate-content#UrlMetadata`) } ] } ``` |

## UrlMetadata

Context of the a single url retrieval.
Fields `retrievedUrl` `string` Retrieved url by the tool.
`urlRetrievalStatus` ``enum (`https://ai.google.dev/api/generate-content#UrlRetrievalStatus`)`` Status of the url retrieval.

| JSON representation |
|---|
| ``` { "retrievedUrl": string, "urlRetrievalStatus": enum (`https://ai.google.dev/api/generate-content#UrlRetrievalStatus`) } ``` |

## UrlRetrievalStatus

Status of the url retrieval.

| Enums ||
|---|---|
| `URL_RETRIEVAL_STATUS_UNSPECIFIED` | Default value. This value is unused. |
| `URL_RETRIEVAL_STATUS_SUCCESS` | Url retrieval is successful. |
| `URL_RETRIEVAL_STATUS_ERROR` | Url retrieval is failed due to error. |
| `URL_RETRIEVAL_STATUS_PAYWALL` | Url retrieval is failed because the content is behind paywall. |
| `URL_RETRIEVAL_STATUS_UNSAFE` | Url retrieval is failed because the content is unsafe. |

## CitationMetadata

- [JSON representation](https://ai.google.dev/api/generate-content#SCHEMA_REPRESENTATION)
- [CitationSource](https://ai.google.dev/api/generate-content#CitationSource)
  - [JSON representation](https://ai.google.dev/api/generate-content#CitationSource.SCHEMA_REPRESENTATION)

A collection of source attributions for a piece of content.
Fields `citationSources[]` ``object (`https://ai.google.dev/api/generate-content#CitationSource`)`` Citations to sources for a specific response.

| JSON representation |
|---|
| ``` { "citationSources": [ { object (`https://ai.google.dev/api/generate-content#CitationSource`) } ] } ``` |

## CitationSource

A citation to a source for a portion of a specific response.
Fields `startIndex` `integer` Optional. Start of segment of the response that is attributed to this source.

Index indicates the start of the segment, measured in bytes.
`endIndex` `integer` Optional. End of the attributed segment, exclusive.
`uri` `string` Optional. URI that is attributed as a source for a portion of the text.
`license` `string` Optional. License for the GitHub project that is attributed as a source for segment.

License info is required for code citations.

| JSON representation |
|---|
| ``` { "startIndex": integer, "endIndex": integer, "uri": string, "license": string } ``` |

## GenerationConfig

- [JSON representation](https://ai.google.dev/api/generate-content#SCHEMA_REPRESENTATION)
- [Modality](https://ai.google.dev/api/generate-content#Modality)
- [SpeechConfig](https://ai.google.dev/api/generate-content#SpeechConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#SpeechConfig.SCHEMA_REPRESENTATION)
- [VoiceConfig](https://ai.google.dev/api/generate-content#VoiceConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#VoiceConfig.SCHEMA_REPRESENTATION)
- [PrebuiltVoiceConfig](https://ai.google.dev/api/generate-content#PrebuiltVoiceConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#PrebuiltVoiceConfig.SCHEMA_REPRESENTATION)
- [MultiSpeakerVoiceConfig](https://ai.google.dev/api/generate-content#MultiSpeakerVoiceConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#MultiSpeakerVoiceConfig.SCHEMA_REPRESENTATION)
- [SpeakerVoiceConfig](https://ai.google.dev/api/generate-content#SpeakerVoiceConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#SpeakerVoiceConfig.SCHEMA_REPRESENTATION)
- [ThinkingConfig](https://ai.google.dev/api/generate-content#ThinkingConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#ThinkingConfig.SCHEMA_REPRESENTATION)
- [ThinkingLevel](https://ai.google.dev/api/generate-content#ThinkingLevel)
- [ImageConfig](https://ai.google.dev/api/generate-content#ImageConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#ImageConfig.SCHEMA_REPRESENTATION)
- [MediaResolution](https://ai.google.dev/api/generate-content#MediaResolution)
- [ResponseFormatConfig](https://ai.google.dev/api/generate-content#ResponseFormatConfig)
  - [JSON representation](https://ai.google.dev/api/generate-content#ResponseFormatConfig.SCHEMA_REPRESENTATION)
- [TextResponseFormat](https://ai.google.dev/api/generate-content#TextResponseFormat)
  - [JSON representation](https://ai.google.dev/api/generate-content#TextResponseFormat.SCHEMA_REPRESENTATION)
- [MimeType](https://ai.google.dev/api/generate-content#MimeType)
- [AudioResponseFormat](https://ai.google.dev/api/generate-content#AudioResponseFormat)
  - [JSON representation](https://ai.google.dev/api/generate-content#AudioResponseFormat.SCHEMA_REPRESENTATION)
- [MimeType](https://ai.google.dev/api/generate-content#MimeType_1)
- [Delivery](https://ai.google.dev/api/generate-content#Delivery)
- [ImageResponseFormat](https://ai.google.dev/api/generate-content#ImageResponseFormat)
  - [JSON representation](https://ai.google.dev/api/generate-content#ImageResponseFormat.SCHEMA_REPRESENTATION)
- [MimeType](https://ai.google.dev/api/generate-content#MimeType_2)
- [Delivery](https://ai.google.dev/api/generate-content#Delivery_1)
- [AspectRatio](https://ai.google.dev/api/generate-content#AspectRatio)
- [ImageSize](https://ai.google.dev/api/generate-content#ImageSize)

Configuration options for model generation and outputs. Not all parameters are configurable for every model.
Fields `stopSequences[]` `string` Optional. The set of character sequences (up to 5) that will stop output generation. If specified, the API will stop at the first appearance of a `stop_sequence`. The stop sequence will not be included as part of the response.
`responseMimeType` `string` Optional. MIME type of the generated candidate text. Supported MIME types are: `text/plain`: (default) Text output. `application/json`: JSON response in the response candidates. `text/x.enum`: ENUM as a string response in the response candidates. Refer to the [docs](https://ai.google.dev/gemini-api/docs/prompting_with_media#plain_text_formats) for a list of all supported text MIME types.
`responseSchema` ``object (`https://ai.google.dev/api/caching#Schema`)`` Optional. Output schema of the generated candidate text. Schemas must be a subset of the [OpenAPI schema](https://spec.openapis.org/oas/v3.0.3#schema) and can be objects, primitives or arrays.

If set, a compatible `responseMimeType` must also be set. Compatible MIME types: `application/json`: Schema for JSON response. Refer to the [JSON text generation guide](https://ai.google.dev/gemini-api/docs/json-mode) for more details.
`_responseJsonSchema` ``value (`https://protobuf.dev/reference/protobuf/google.protobuf#value` format)`` Optional. Output schema of the generated response. This is an alternative to `responseSchema` that accepts [JSON Schema](https://json-schema.org/).

If set, `responseSchema` must be omitted, but `responseMimeType` is required.

While the full JSON Schema may be sent, not all features are supported. Specifically, only the following properties are supported:

- `$id`
- `$defs`
- `$ref`
- `$anchor`
- `type`
- `format`
- `title`
- `description`
- `enum` (for strings and numbers)
- `items`
- `prefixItems`
- `minItems`
- `maxItems`
- `minimum`
- `maximum`
- `anyOf`
- `oneOf` (interpreted the same as `anyOf`)
- `properties`
- `additionalProperties`
- `required`

The non-standard `propertyOrdering` property may also be set.

Cyclic references are unrolled to a limited degree and, as such, may only be used within non-required properties. (Nullable properties are not sufficient.) If `$ref` is set on a sub-schema, no other properties, except for than those starting as a `$`, may be set.
`responseJsonSchema` ``value (`https://protobuf.dev/reference/protobuf/google.protobuf#value` format)`` Optional. An internal detail. Use `responseJsonSchema` rather than this field.
`responseModalities[]` ``enum (`https://ai.google.dev/api/generate-content#Modality`)`` Optional. The requested modalities of the response. Represents the set of modalities that the model can return, and should be expected in the response. This is an exact match to the modalities of the response.

A model may have multiple combinations of supported modalities. If the requested modalities do not match any of the supported combinations, an error will be returned.

An empty list is equivalent to requesting only text.
`candidateCount` `integer` Optional. Number of generated responses to return. If unset, this will default to 1. Please note that this doesn't work for previous generation models (Gemini 1.0 family)
`maxOutputTokens` `integer` Optional. The maximum number of tokens to include in a response candidate.

Note: The default value varies by model, see the `Model.output_token_limit` attribute of the `Model` returned from the `getModel` function.
`temperature` `number` Optional. Controls the randomness of the output.

Note: The default value varies by model, see the `Model.temperature` attribute of the `Model` returned from the `getModel` function.

Values can range from \[0.0, 2.0\].
`topP` `number` Optional. The maximum cumulative probability of tokens to consider when sampling.

The model uses combined Top-k and Top-p (nucleus) sampling.

Tokens are sorted based on their assigned probabilities so that only the most likely tokens are considered. Top-k sampling directly limits the maximum number of tokens to consider, while Nucleus sampling limits the number of tokens based on the cumulative probability.

Note: The default value varies by `Model` and is specified by the`Model.top_p` attribute returned from the `getModel` function. An empty `topK` attribute indicates that the model doesn't apply top-k sampling and doesn't allow setting `topK` on requests.
`topK` `integer` Optional. The maximum number of tokens to consider when sampling.

Gemini models use Top-p (nucleus) sampling or a combination of Top-k and nucleus sampling. Top-k sampling considers the set of `topK` most probable tokens. Models running with nucleus sampling don't allow topK setting.

Note: The default value varies by `Model` and is specified by the`Model.top_p` attribute returned from the `getModel` function. An empty `topK` attribute indicates that the model doesn't apply top-k sampling and doesn't allow setting `topK` on requests.
`seed` `integer` Optional. Seed used in decoding. If not set, the request uses a randomly generated seed.
`presencePenalty` `number` Optional. Presence penalty applied to the next token's logprobs if the token has already been seen in the response.

This penalty is binary on/off and not dependant on the number of times the token is used (after the first). Use `https://ai.google.dev/api/generate-content#FIELDS.frequency_penalty` for a penalty that increases with each use.

A positive penalty will discourage the use of tokens that have already been used in the response, increasing the vocabulary.

A negative penalty will encourage the use of tokens that have already been used in the response, decreasing the vocabulary.
`frequencyPenalty` `number` Optional. Frequency penalty applied to the next token's logprobs, multiplied by the number of times each token has been seen in the respponse so far.

A positive penalty will discourage the use of tokens that have already been used, proportional to the number of times the token has been used: The more a token is used, the more difficult it is for the model to use that token again increasing the vocabulary of responses.

Caution: A *negative* penalty will encourage the model to reuse tokens proportional to the number of times the token has been used. Small negative values will reduce the vocabulary of a response. Larger negative values will cause the model to start repeating a common token until it hits the `https://ai.google.dev/api/generate-content#FIELDS.max_output_tokens` limit.
`responseLogprobs` `boolean` Optional. If true, export the logprobs results in response.
`logprobs` `integer` Optional. Only valid if `https://ai.google.dev/api/generate-content#FIELDS.response_logprobs`. This sets the number of top logprobs, including the chosen candidate, to return at each decoding step in the `https://ai.google.dev/api/generate-content#FIELDS.logprobs_result`. The number must be in the range of \[0, 20\].
`enableEnhancedCivicAnswers` `boolean` Optional. Enables enhanced civic answers. It may not be available for all models.
`speechConfig` ``object (`https://ai.google.dev/api/generate-content#SpeechConfig`)`` Optional. The speech generation config.
`thinkingConfig` ``object (`https://ai.google.dev/api/generate-content#ThinkingConfig`)`` Optional. Config for thinking features. An error will be returned if this field is set for models that don't support thinking.
`imageConfig` ``object (`https://ai.google.dev/api/generate-content#ImageConfig`)`` Optional. Config for image generation. An error will be returned if this field is set for models that don't support these config options.
`mediaResolution` ``enum (`https://ai.google.dev/api/generate-content#MediaResolution`)`` Optional. If specified, the media resolution specified will be used.
`responseFormat` ``object (`https://ai.google.dev/api/generate-content#ResponseFormatConfig`)`` Optional. Configuration for the response output format. Allows specifying output configuration per modality (text, audio, image) in a flat structure.

| JSON representation |
|---|
| ``` { "stopSequences": [ string ], "responseMimeType": string, "responseSchema": { object (`https://ai.google.dev/api/caching#Schema`) }, "_responseJsonSchema": value, "responseJsonSchema": value, "responseModalities": [ enum (`https://ai.google.dev/api/generate-content#Modality`) ], "candidateCount": integer, "maxOutputTokens": integer, "temperature": number, "topP": number, "topK": integer, "seed": integer, "presencePenalty": number, "frequencyPenalty": number, "responseLogprobs": boolean, "logprobs": integer, "enableEnhancedCivicAnswers": boolean, "speechConfig": { object (`https://ai.google.dev/api/generate-content#SpeechConfig`) }, "thinkingConfig": { object (`https://ai.google.dev/api/generate-content#ThinkingConfig`) }, "imageConfig": { object (`https://ai.google.dev/api/generate-content#ImageConfig`) }, "mediaResolution": enum (`https://ai.google.dev/api/generate-content#MediaResolution`), "responseFormat": { object (`https://ai.google.dev/api/generate-content#ResponseFormatConfig`) } } ``` |

## Modality

Supported modalities of the response.

| Enums ||
|---|---|
| `MODALITY_UNSPECIFIED` | Default value. |
| `TEXT` | Indicates the model should return text. |
| `IMAGE` | Indicates the model should return images. |
| `AUDIO` | Indicates the model should return audio. |

## SpeechConfig

Config for speech generation and transcription.
Fields `voiceConfig` ``object (`https://ai.google.dev/api/generate-content#VoiceConfig`)`` The configuration in case of single-voice output.
`multiSpeakerVoiceConfig` ``object (`https://ai.google.dev/api/generate-content#MultiSpeakerVoiceConfig`)`` Optional. The configuration for the multi-speaker setup. It is mutually exclusive with the voiceConfig field.
`languageCode` `string` Optional. The IETF [BCP-47](https://www.rfc-editor.org/rfc/bcp/bcp47.txt) language code that the user configured the app to use. Used for speech recognition and synthesis.

Valid values are: `de-DE`, `en-AU`, `en-GB`, `en-IN`, `en-US`, `es-US`, `fr-FR`, `hi-IN`, `pt-BR`, `ar-XA`, `es-ES`, `fr-CA`, `id-ID`, `it-IT`, `ja-JP`, `tr-TR`, `vi-VN`, `bn-IN`, `gu-IN`, `kn-IN`, `ml-IN`, `mr-IN`, `ta-IN`, `te-IN`, `nl-NL`, `ko-KR`, `cmn-CN`, `pl-PL`, `ru-RU`, and `th-TH`.

| JSON representation |
|---|
| ``` { "voiceConfig": { object (`https://ai.google.dev/api/generate-content#VoiceConfig`) }, "multiSpeakerVoiceConfig": { object (`https://ai.google.dev/api/generate-content#MultiSpeakerVoiceConfig`) }, "languageCode": string } ``` |

## VoiceConfig

The configuration for the voice to use.
Fields `voice_config` `Union type` The configuration for the speaker to use. `voice_config` can be only one of the following: `prebuiltVoiceConfig` ``object (`https://ai.google.dev/api/generate-content#PrebuiltVoiceConfig`)`` The configuration for the prebuilt voice to use.

| JSON representation |
|---|
| ``` { // voice_config "prebuiltVoiceConfig": { object (`https://ai.google.dev/api/generate-content#PrebuiltVoiceConfig`) } // Union type } ``` |

## PrebuiltVoiceConfig

The configuration for the prebuilt speaker to use.
Fields `voiceName` `string` The name of the preset voice to use.

| JSON representation |
|---|
| ``` { "voiceName": string } ``` |

## MultiSpeakerVoiceConfig

The configuration for the multi-speaker setup.
Fields `speakerVoiceConfigs[]` ``object (`https://ai.google.dev/api/generate-content#SpeakerVoiceConfig`)`` Required. All the enabled speaker voices.

| JSON representation |
|---|
| ``` { "speakerVoiceConfigs": [ { object (`https://ai.google.dev/api/generate-content#SpeakerVoiceConfig`) } ] } ``` |

## SpeakerVoiceConfig

The configuration for a single speaker in a multi speaker setup.
Fields `speaker` `string` Required. The name of the speaker to use. Should be the same as in the prompt.
`voiceConfig` ``object (`https://ai.google.dev/api/generate-content#VoiceConfig`)`` Required. The configuration for the voice to use.

| JSON representation |
|---|
| ``` { "speaker": string, "voiceConfig": { object (`https://ai.google.dev/api/generate-content#VoiceConfig`) } } ``` |

## ThinkingConfig

Config for thinking features.
Fields `includeThoughts` `boolean` Indicates whether to include thoughts in the response. If true, thoughts are returned only when available.
`thinkingBudget` `integer` The number of thoughts tokens that the model should generate.
`thinkingLevel` ``enum (`https://ai.google.dev/api/generate-content#ThinkingLevel`)`` Optional. Controls the maximum depth of the model's internal reasoning process before it produces a response. The default value is model-dependent. Refer to the [Thinking levels guide](https://ai.google.dev/gemini-api/docs/thinking#thinking-levels) for more details. Recommended for Gemini 3 or later models. Use with earlier models results in an error.

| JSON representation |
|---|
| ``` { "includeThoughts": boolean, "thinkingBudget": integer, "thinkingLevel": enum (`https://ai.google.dev/api/generate-content#ThinkingLevel`) } ``` |

## ThinkingLevel

Allow user to specify how much to think using enum instead of integer budget.

| Enums ||
|---|---|
| `THINKING_LEVEL_UNSPECIFIED` | Default value. |
| `MINIMAL` | Little to no thinking. |
| `LOW` | Low thinking level. |
| `MEDIUM` | Medium thinking level. |
| `HIGH` | High thinking level. |

## ImageConfig

Config for image generation features.
Fields `aspectRatio` `string` Optional. The aspect ratio of the image to generate. Supported aspect ratios: `1:1`, `1:4`, `4:1`, `1:8`, `8:1`, `2:3`, `3:2`, `3:4`, `4:3`, `4:5`, `5:4`, `9:16`, `16:9`, or `21:9`.

If not specified, the model will choose a default aspect ratio based on any reference images provided.
`imageSize` `string` Optional. Specifies the size of generated images. Supported values are `512`, `1K`, `2K`, `4K`. If not specified, the model will use default value `1K`.

| JSON representation |
|---|
| ``` { "aspectRatio": string, "imageSize": string } ``` |

## MediaResolution

Media resolution for the input media.

| Enums ||
|---|---|
| `MEDIA_RESOLUTION_UNSPECIFIED` | Media resolution has not been set. |
| `MEDIA_RESOLUTION_LOW` | Media resolution set to low (64 tokens). |
| `MEDIA_RESOLUTION_MEDIUM` | Media resolution set to medium (256 tokens). |
| `MEDIA_RESOLUTION_HIGH` | Media resolution set to high (zoomed reframing with 256 tokens). |

## ResponseFormatConfig

Configuration for the response output format. This is a flat object where each optional sub-field configures a specific output modality.
Fields `text` ``object (`https://ai.google.dev/api/generate-content#TextResponseFormat`)`` Optional. Text output format configuration.
`audio` ``object (`https://ai.google.dev/api/generate-content#AudioResponseFormat`)`` Optional. Audio output format configuration.
`image` ``object (`https://ai.google.dev/api/generate-content#ImageResponseFormat`)`` Optional. Image output format configuration.

| JSON representation |
|---|
| ``` { "text": { object (`https://ai.google.dev/api/generate-content#TextResponseFormat`) }, "audio": { object (`https://ai.google.dev/api/generate-content#AudioResponseFormat`) }, "image": { object (`https://ai.google.dev/api/generate-content#ImageResponseFormat`) } } ``` |

## TextResponseFormat

Configuration for text output format.
Fields `mimeType` ``enum (`https://ai.google.dev/api/generate-content#MimeType`)`` Optional. The MIME type of the text output.
`schema` ``value (`https://protobuf.dev/reference/protobuf/google.protobuf#value` format)`` Optional. The JSON schema that the output should conform to. Only applicable when mimeType is APPLICATION_JSON.

| JSON representation |
|---|
| ``` { "mimeType": enum (`https://ai.google.dev/api/generate-content#MimeType`), "schema": value } ``` |

## MimeType

Supported MIME types for text output.

| Enums ||
|---|---|
| `MIME_TYPE_UNSPECIFIED` | Default value. This value is unused. |
| `APPLICATION_JSON` | JSON output format. |
| `TEXT_PLAIN` | Plain text output format. |

## AudioResponseFormat

Configuration for audio output format.
Fields `mimeType` ``enum (`https://ai.google.dev/api/generate-content#MimeType_1`)`` Optional. The MIME type of the audio output.
`delivery` ``enum (`https://ai.google.dev/api/generate-content#Delivery`)`` Optional. The delivery mode for the audio output.
`sampleRate` `integer` Optional. Sample rate in Hz.
`bitRate` `integer` Optional. Bit rate in bits per second (bps). Only applicable for compressed formats (MP3, Opus).

| JSON representation |
|---|
| ``` { "mimeType": enum (`https://ai.google.dev/api/generate-content#MimeType_1`), "delivery": enum (`https://ai.google.dev/api/generate-content#Delivery`), "sampleRate": integer, "bitRate": integer } ``` |

## MimeType

Supported MIME types for audio output.

| Enums ||
|---|---|
| `MIME_TYPE_UNSPECIFIED` | Default value. This value is unused. |
| `AUDIO_MP3` | MP3 audio format. |
| `AUDIO_OGG_OPUS` | OGG Opus audio format. |
| `AUDIO_L16` | Raw PCM (L16) audio format. |
| `AUDIO_WAV` | WAV audio format. |
| `AUDIO_ALAW` | A-law audio format. |
| `AUDIO_MULAW` | Mu-law audio format. |

## Delivery

Delivery mode for audio output.

| Enums ||
|---|---|
| `DELIVERY_UNSPECIFIED` | Default value. This value is unused. |
| `INLINE` | Audio data is returned inline in the response. |
| `URI` | Audio data is returned as a URI. |

## ImageResponseFormat

Configuration for image output format.
Fields `mimeType` ``enum (`https://ai.google.dev/api/generate-content#MimeType_2`)`` Optional. The MIME type of the image output.
`delivery` ``enum (`https://ai.google.dev/api/generate-content#Delivery_1`)`` Optional. The delivery mode for the image output.
`aspectRatio` ``enum (`https://ai.google.dev/api/generate-content#AspectRatio`)`` Optional. The aspect ratio for the image output.
`imageSize` ``enum (`https://ai.google.dev/api/generate-content#ImageSize`)`` Optional. The size of the image output.

| JSON representation |
|---|
| ``` { "mimeType": enum (`https://ai.google.dev/api/generate-content#MimeType_2`), "delivery": enum (`https://ai.google.dev/api/generate-content#Delivery_1`), "aspectRatio": enum (`https://ai.google.dev/api/generate-content#AspectRatio`), "imageSize": enum (`https://ai.google.dev/api/generate-content#ImageSize`) } ``` |

## MimeType

Supported MIME types for image output.

| Enums ||
|---|---|
| `MIME_TYPE_UNSPECIFIED` | Default value. This value is unused. |
| `IMAGE_JPEG` | JPEG image format. |

## Delivery

Delivery mode for image output.

| Enums ||
|---|---|
| `DELIVERY_UNSPECIFIED` | Default value. This value is unused. |
| `INLINE` | Image data is returned inline in the response. |
| `URI` | Image data is returned as a URI. |

## AspectRatio

Supported aspect ratios for image output.

| Enums ||
|---|---|
| `ASPECT_RATIO_UNSPECIFIED` | Default value. This value is unused. |
| `ASPECT_RATIO_ONE_BY_ONE` | 1:1 aspect ratio. |
| `ASPECT_RATIO_TWO_BY_THREE` | 2:3 aspect ratio. |
| `ASPECT_RATIO_THREE_BY_TWO` | 3:2 aspect ratio. |
| `ASPECT_RATIO_THREE_BY_FOUR` | 3:4 aspect ratio. |
| `ASPECT_RATIO_FOUR_BY_THREE` | 4:3 aspect ratio. |
| `ASPECT_RATIO_FOUR_BY_FIVE` | 4:5 aspect ratio. |
| `ASPECT_RATIO_FIVE_BY_FOUR` | 5:4 aspect ratio. |
| `ASPECT_RATIO_NINE_BY_SIXTEEN` | 9:16 aspect ratio. |
| `ASPECT_RATIO_SIXTEEN_BY_NINE` | 16:9 aspect ratio. |
| `ASPECT_RATIO_TWENTY_ONE_BY_NINE` | 21:9 aspect ratio. |
| `ASPECT_RATIO_ONE_BY_EIGHT` | 1:8 aspect ratio. |
| `ASPECT_RATIO_EIGHT_BY_ONE` | 8:1 aspect ratio. |
| `ASPECT_RATIO_ONE_BY_FOUR` | 1:4 aspect ratio. |
| `ASPECT_RATIO_FOUR_BY_ONE` | 4:1 aspect ratio. |

## ImageSize

Supported image sizes for image output.

| Enums ||
|---|---|
| `IMAGE_SIZE_UNSPECIFIED` | Default value. This value is unused. |
| `IMAGE_SIZE_FIVE_TWELVE` | 512px image size. |
| `IMAGE_SIZE_ONE_K` | 1K image size. |
| `IMAGE_SIZE_TWO_K` | 2K image size. |
| `IMAGE_SIZE_FOUR_K` | 4K image size. |

## HarmCategory

The category of a rating.

These categories cover various kinds of harms that developers may wish to adjust.

| Enums ||
|---|---|
| `HARM_CATEGORY_UNSPECIFIED` | Category is unspecified. |
| `HARM_CATEGORY_DEROGATORY` | **PaLM** - Negative or harmful comments targeting identity and/or protected attribute. |
| `HARM_CATEGORY_TOXICITY` | **PaLM** - Content that is rude, disrespectful, or profane. |
| `HARM_CATEGORY_VIOLENCE` | **PaLM** - Describes scenarios depicting violence against an individual or group, or general descriptions of gore. |
| `HARM_CATEGORY_SEXUAL` | **PaLM** - Contains references to sexual acts or other lewd content. |
| `HARM_CATEGORY_MEDICAL` | **PaLM** - Promotes unchecked medical advice. |
| `HARM_CATEGORY_DANGEROUS` | **PaLM** - Dangerous content that promotes, facilitates, or encourages harmful acts. |
| `HARM_CATEGORY_HARASSMENT` | **Gemini** - Harassment content. |
| `HARM_CATEGORY_HATE_SPEECH` | **Gemini** - Hate speech and content. |
| `HARM_CATEGORY_SEXUALLY_EXPLICIT` | **Gemini** - Sexually explicit content. |
| `HARM_CATEGORY_DANGEROUS_CONTENT` | **Gemini** - Dangerous content. |
| `HARM_CATEGORY_CIVIC_INTEGRITY` | **Gemini** - Content that may be used to harm civic integrity. DEPRECATED: use enableEnhancedCivicAnswers instead. > [!WARNING] > This item is deprecated! |

## ModalityTokenCount

- [JSON representation](https://ai.google.dev/api/generate-content#SCHEMA_REPRESENTATION)
- [Modality](https://ai.google.dev/api/generate-content#Modality)

Represents token counting info for a single modality.
Fields `modality` ``enum (`https://ai.google.dev/api/generate-content#Modality`)`` The modality associated with this token count.
`tokenCount` `integer` Number of tokens.

| JSON representation |
|---|
| ``` { "modality": enum (`https://ai.google.dev/api/generate-content#Modality`), "tokenCount": integer } ``` |

## Modality

Content Part modality

| Enums ||
|---|---|
| `MODALITY_UNSPECIFIED` | Unspecified modality. |
| `TEXT` | Plain text. |
| `IMAGE` | Image. |
| `VIDEO` | Video. |
| `AUDIO` | Audio. |
| `DOCUMENT` | Document, e.g. PDF. |

## SafetyRating

- [JSON representation](https://ai.google.dev/api/generate-content#SCHEMA_REPRESENTATION)
- [HarmProbability](https://ai.google.dev/api/generate-content#HarmProbability)

Safety rating for a piece of content.

The safety rating contains the category of harm and the harm probability level in that category for a piece of content. Content is classified for safety across a number of harm categories and the probability of the harm classification is included here.
Fields `category` ``enum (`https://ai.google.dev/api/generate-content#v1beta.HarmCategory`)`` Required. The category for this rating.
`probability` ``enum (`https://ai.google.dev/api/generate-content#HarmProbability`)`` Required. The probability of harm for this content.
`blocked` `boolean` Was this content blocked because of this rating?

| JSON representation |
|---|
| ``` { "category": enum (`https://ai.google.dev/api/generate-content#v1beta.HarmCategory`), "probability": enum (`https://ai.google.dev/api/generate-content#HarmProbability`), "blocked": boolean } ``` |

## HarmProbability

The probability that a piece of content is harmful.

The classification system gives the probability of the content being unsafe. This does not indicate the severity of harm for a piece of content.

| Enums ||
|---|---|
| `HARM_PROBABILITY_UNSPECIFIED` | Probability is unspecified. |
| `NEGLIGIBLE` | Content has a negligible chance of being unsafe. |
| `LOW` | Content has a low chance of being unsafe. |
| `MEDIUM` | Content has a medium chance of being unsafe. |
| `HIGH` | Content has a high chance of being unsafe. |

## SafetySetting

- [JSON representation](https://ai.google.dev/api/generate-content#SCHEMA_REPRESENTATION)
- [HarmBlockThreshold](https://ai.google.dev/api/generate-content#HarmBlockThreshold)

Safety setting, affecting the safety-blocking behavior.

Passing a safety setting for a category changes the allowed probability that content is blocked.
Fields `category` ``enum (`https://ai.google.dev/api/generate-content#v1beta.HarmCategory`)`` Required. The category for this setting.
`threshold` ``enum (`https://ai.google.dev/api/generate-content#HarmBlockThreshold`)`` Required. Controls the probability threshold at which harm is blocked.

| JSON representation |
|---|
| ``` { "category": enum (`https://ai.google.dev/api/generate-content#v1beta.HarmCategory`), "threshold": enum (`https://ai.google.dev/api/generate-content#HarmBlockThreshold`) } ``` |

## HarmBlockThreshold

Block at and beyond a specified harm probability.

| Enums ||
|---|---|
| `HARM_BLOCK_THRESHOLD_UNSPECIFIED` | Threshold is unspecified. |
| `BLOCK_LOW_AND_ABOVE` | Content with NEGLIGIBLE will be allowed. |
| `BLOCK_MEDIUM_AND_ABOVE` | Content with NEGLIGIBLE and LOW will be allowed. |
| `BLOCK_ONLY_HIGH` | Content with NEGLIGIBLE, LOW, and MEDIUM will be allowed. |
| `BLOCK_NONE` | All content will be allowed. |
| `OFF` | Turn off the safety filter. |

## ServiceTier

Service tier of the request.

| Enums ||
|---|---|
| `unspecified` | Default service tier, which is standard. |
| `standard` | Standard service tier. |
| `flex` | Flex service tier. |
| `priority` | Priority service tier. |