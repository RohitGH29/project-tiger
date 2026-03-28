"""Small demo script that uses a third-party package."""

import requests


def main() -> None:
    response = requests.get("https://httpbin.org/get", timeout=15)
    response.raise_for_status()
    data = response.json()
    print("Fetched URL:", data.get("url"))
    print("HTTP status:", response.status_code)


if __name__ == "__main__":
    main()
