# Google Calendar OAuth - Frontend Integration Guide

## 🎯 Overview

This guide shows how to integrate the Google Calendar OAuth backend with your frontend application.

## 🔄 Complete Integration Flow

```
┌──────────────────────────────────────────────────────────────┐
│                    Frontend Flow                              │
└──────────────────────────────────────────────────────────────┘

1. User clicks "Connect Google Calendar"
   ↓
2. Frontend initiates Google OAuth flow
   ↓
3. User authorizes in Google popup
   ↓
4. Google redirects back with auth code
   ↓
5. Frontend exchanges code for tokens
   ↓
6. Frontend sends tokens to backend API
   ↓
7. Backend encrypts and stores tokens
   ↓
8. Success! Calendar is connected
```

## 📝 Step-by-Step Implementation

### Step 1: Install Google OAuth Library

```bash
npm install @react-oauth/google
# or
yarn add @react-oauth/google
```

### Step 2: Setup Google OAuth Provider

```jsx
// App.jsx or main entry point
import { GoogleOAuthProvider } from '@react-oauth/google';

function App() {
  return (
    <GoogleOAuthProvider clientId="YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com">
      <YourApp />
    </GoogleOAuthProvider>
  );
}
```

### Step 3: Create Google Calendar Connection Component

```jsx
// components/GoogleCalendarConnect.jsx
import React, { useState, useEffect } from 'react';
import { useGoogleLogin } from '@react-oauth/google';
import { useAuth } from '@clerk/clerk-react'; // or your auth provider

const GoogleCalendarConnect = ({ chatbotId }) => {
  const [isConnected, setIsConnected] = useState(false);
  const [isExpired, setIsExpired] = useState(false);
  const [expiresAt, setExpiresAt] = useState(null);
  const [loading, setLoading] = useState(false);
  const { getToken } = useAuth();

  const API_BASE_URL = 'http://localhost:8080/v1/api/chatbot/google-calendar';

  // Check connection status on mount
  useEffect(() => {
    checkConnectionStatus();
  }, [chatbotId]);

  const checkConnectionStatus = async () => {
    try {
      const token = await getToken();
      const response = await fetch(`${API_BASE_URL}/${chatbotId}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      const data = await response.json();
      setIsConnected(data.connected);
      if (data.connected) {
        setIsExpired(data.isExpired);
        setExpiresAt(data.expiresAt);
      }
    } catch (error) {
      console.error('Error checking connection status:', error);
    }
  };

  // Google OAuth login handler
  const login = useGoogleLogin({
    onSuccess: async (tokenResponse) => {
      await storeTokens(tokenResponse);
    },
    onError: (error) => {
      console.error('Google login failed:', error);
      alert('Failed to connect Google Calendar');
    },
    scope: 'https://www.googleapis.com/auth/calendar',
    flow: 'implicit', // Use 'auth-code' for better security in production
  });

  const storeTokens = async (googleTokens) => {
    setLoading(true);
    try {
      const token = await getToken();
      
      const response = await fetch(`${API_BASE_URL}/${chatbotId}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          accessToken: googleTokens.access_token,
          refreshToken: googleTokens.refresh_token || 'dummy_refresh_token', // For implicit flow
          expiresIn: googleTokens.expires_in,
          tokenType: googleTokens.token_type || 'Bearer',
        }),
      });

      if (!response.ok) {
        throw new Error('Failed to store tokens');
      }

      const data = await response.json();
      console.log('Tokens stored successfully:', data);
      
      // Refresh status
      await checkConnectionStatus();
      alert('Google Calendar connected successfully!');
    } catch (error) {
      console.error('Error storing tokens:', error);
      alert('Failed to store tokens. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const disconnect = async () => {
    if (!confirm('Are you sure you want to disconnect Google Calendar?')) {
      return;
    }

    setLoading(true);
    try {
      const token = await getToken();
      
      const response = await fetch(`${API_BASE_URL}/${chatbotId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        throw new Error('Failed to disconnect');
      }

      const data = await response.json();
      console.log('Disconnected successfully:', data);
      
      setIsConnected(false);
      setIsExpired(false);
      setExpiresAt(null);
      alert('Google Calendar disconnected successfully!');
    } catch (error) {
      console.error('Error disconnecting:', error);
      alert('Failed to disconnect. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const refreshToken = async () => {
    setLoading(true);
    try {
      const token = await getToken();
      
      const response = await fetch(`${API_BASE_URL}/${chatbotId}/refresh`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        throw new Error('Failed to refresh token');
      }

      const data = await response.json();
      console.log('Token refreshed:', data);
      
      await checkConnectionStatus();
      alert('Token refreshed successfully!');
    } catch (error) {
      console.error('Error refreshing token:', error);
      alert('Failed to refresh token. Please reconnect.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="google-calendar-connect">
      <h3>Google Calendar Integration</h3>
      
      {!isConnected ? (
        <div>
          <p>Connect your Google Calendar to enable calendar events.</p>
          <button 
            onClick={() => login()} 
            disabled={loading}
            className="connect-button"
          >
            {loading ? 'Connecting...' : 'Connect Google Calendar'}
          </button>
        </div>
      ) : (
        <div>
          <p>✅ Google Calendar is connected</p>
          {expiresAt && (
            <p className="expires-info">
              Token expires: {new Date(expiresAt).toLocaleString()}
              {isExpired && <span className="expired-badge"> (Expired - will auto-refresh)</span>}
            </p>
          )}
          <div className="button-group">
            <button 
              onClick={refreshToken} 
              disabled={loading}
              className="refresh-button"
            >
              Refresh Token
            </button>
            <button 
              onClick={disconnect} 
              disabled={loading}
              className="disconnect-button"
            >
              {loading ? 'Disconnecting...' : 'Disconnect'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default GoogleCalendarConnect;
```

### Step 4: Add Styling (Optional)

```css
/* GoogleCalendarConnect.css */
.google-calendar-connect {
  padding: 20px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  max-width: 500px;
}

.google-calendar-connect h3 {
  margin-top: 0;
  color: #333;
}

.connect-button {
  background-color: #4285f4;
  color: white;
  padding: 12px 24px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
}

.connect-button:hover:not(:disabled) {
  background-color: #357ae8;
}

.connect-button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.button-group {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.refresh-button {
  background-color: #34a853;
  color: white;
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.disconnect-button {
  background-color: #ea4335;
  color: white;
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.expires-info {
  font-size: 14px;
  color: #666;
}

.expired-badge {
  color: #ea4335;
  font-weight: bold;
}
```

## 🔐 Production Setup (Authorization Code Flow)

For production, use the **Authorization Code Flow** which is more secure:

```jsx
// More secure implementation for production
const login = useGoogleLogin({
  onSuccess: async (codeResponse) => {
    // Exchange code for tokens on your backend
    await exchangeCodeForTokens(codeResponse.code);
  },
  flow: 'auth-code',
  scope: 'https://www.googleapis.com/auth/calendar',
  ux_mode: 'popup',
});

const exchangeCodeForTokens = async (code) => {
  setLoading(true);
  try {
    const token = await getToken();
    
    // Call your backend endpoint that exchanges code for tokens
    const response = await fetch(`${API_BASE_URL}/${chatbotId}/exchange-code`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ code }),
    });

    if (!response.ok) {
      throw new Error('Failed to exchange code');
    }

    await checkConnectionStatus();
    alert('Google Calendar connected successfully!');
  } catch (error) {
    console.error('Error exchanging code:', error);
    alert('Failed to connect. Please try again.');
  } finally {
    setLoading(false);
  }
};
```

## 📱 Usage in Your App

```jsx
// pages/ChatbotSettings.jsx
import GoogleCalendarConnect from '../components/GoogleCalendarConnect';

function ChatbotSettings() {
  const chatbotId = "698576e4d5fd040c84aed7d8"; // From your route params

  return (
    <div className="settings-page">
      <h1>Chatbot Settings</h1>
      
      {/* Other settings */}
      
      <section className="integrations-section">
        <h2>Integrations</h2>
        <GoogleCalendarConnect chatbotId={chatbotId} />
      </section>
    </div>
  );
}
```

## 🔑 Environment Variables

Create a `.env` file:

```bash
# Frontend
REACT_APP_GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
REACT_APP_API_BASE_URL=https://api.yourdomain.com

# Backend (already configured)
GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_client_secret
ENCRYPTION_KEY=your_32_byte_hex_key
```

## 🧪 Testing

### Test Connection Flow

```javascript
// Test script
const testGoogleCalendarIntegration = async () => {
  const chatbotId = 'test_chatbot_id';
  const clerkToken = 'your_clerk_jwt_token';
  
  // 1. Check initial status (should be not connected)
  console.log('1. Checking connection status...');
  const statusResponse = await fetch(
    `http://localhost:8080/v1/api/chatbot/google-calendar/${chatbotId}`,
    { headers: { 'Authorization': `Bearer ${clerkToken}` } }
  );
  console.log('Status:', await statusResponse.json());
  
  // 2. Store tokens (simulate OAuth callback)
  console.log('2. Storing tokens...');
  const storeResponse = await fetch(
    `http://localhost:8080/v1/api/chatbot/google-calendar/${chatbotId}`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${clerkToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        accessToken: 'test_access_token',
        refreshToken: 'test_refresh_token',
        expiresIn: 3600,
        tokenType: 'Bearer',
      }),
    }
  );
  console.log('Store result:', await storeResponse.json());
  
  // 3. Get tokens
  console.log('3. Getting tokens...');
  const tokensResponse = await fetch(
    `http://localhost:8080/v1/api/chatbot/google-calendar/${chatbotId}/tokens`,
    { headers: { 'Authorization': `Bearer ${clerkToken}` } }
  );
  console.log('Tokens:', await tokensResponse.json());
  
  // 4. Disconnect
  console.log('4. Disconnecting...');
  const disconnectResponse = await fetch(
    `http://localhost:8080/v1/api/chatbot/google-calendar/${chatbotId}`,
    {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${clerkToken}` },
    }
  );
  console.log('Disconnect result:', await disconnectResponse.json());
};
```

## 🚨 Error Handling

```jsx
const handleApiError = (error, response) => {
  if (response?.status === 401) {
    // Unauthorized - redirect to login
    window.location.href = '/login';
  } else if (response?.status === 403) {
    alert('You do not have permission to manage this chatbot');
  } else if (response?.status === 404) {
    alert('Chatbot not found');
  } else if (response?.status === 410) {
    // Token expired and refresh failed
    alert('Your Google Calendar connection has expired. Please reconnect.');
    setIsConnected(false);
  } else {
    alert('An error occurred. Please try again.');
  }
  console.error('API Error:', error);
};
```

## 📊 TypeScript Types

```typescript
// types/googleCalendar.ts

export interface ConnectionStatus {
  connected: boolean;
  chatbotId: string;
  expiresAt?: string;
  isExpired?: boolean;
}

export interface StoreTokensRequest {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType?: string;
}

export interface StoreTokensResponse {
  success: boolean;
  message: string;
  chatbotId: string;
}

export interface GetTokensResponse {
  success: boolean;
  accessToken: string;
  expiresAt: string;
  tokenType: string;
}

export interface ErrorResponse {
  success: false;
  error: string;
  message: string;
}
```

## 🎨 UI/UX Best Practices

1. **Show Connection Status Clearly**
   - ✅ Connected badge
   - ⚠️ Expired warning
   - ❌ Not connected state

2. **Loading States**
   - Disable buttons during API calls
   - Show loading spinners
   - Provide feedback on success/failure

3. **Error Messages**
   - Clear, user-friendly error messages
   - Suggest actions to resolve issues
   - Log technical details to console

4. **Confirmation Dialogs**
   - Confirm before disconnecting
   - Warn about consequences

## 🔄 Auto-Refresh Pattern

The backend automatically refreshes expired tokens when you call the "Get Tokens" endpoint. In your frontend:

```jsx
// When you need to use the calendar API
const getValidAccessToken = async () => {
  try {
    const token = await getToken();
    const response = await fetch(
      `${API_BASE_URL}/${chatbotId}/tokens`,
      { headers: { 'Authorization': `Bearer ${token}` } }
    );
    
    if (!response.ok) {
      throw new Error('Failed to get token');
    }
    
    const data = await response.json();
    return data.accessToken; // This will be fresh, even if it was expired
  } catch (error) {
    console.error('Error getting access token:', error);
    throw error;
  }
};

// Use it when making calendar API calls
const createCalendarEvent = async (eventData) => {
  const accessToken = await getValidAccessToken();
  
  // Now use this token to call Google Calendar API
  const response = await fetch('https://www.googleapis.com/calendar/v3/calendars/primary/events', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(eventData),
  });
  
  return await response.json();
};
```

## 📚 Next Steps

1. ✅ Implement the frontend component
2. ✅ Add to your chatbot settings page
3. ✅ Configure Google OAuth credentials
4. ✅ Test the complete flow
5. ✅ Deploy and monitor

---

**Need Help?**
- Check the backend API documentation: `GOOGLE_CALENDAR_OAUTH_SETUP.md`
- Review Google OAuth docs: https://developers.google.com/identity/protocols/oauth2
- Check Clerk authentication: https://clerk.com/docs

**Version:** 1.0.0  
**Last Updated:** February 6, 2026








